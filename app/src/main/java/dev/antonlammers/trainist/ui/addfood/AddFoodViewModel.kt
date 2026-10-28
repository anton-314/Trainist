package dev.antonlammers.trainist.ui.addfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.model.BarcodeException
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.domain.repository.CustomFoodRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.FoodSearchRepository
import dev.antonlammers.trainist.domain.repository.SavedMealRepository
import dev.antonlammers.trainist.util.normalizeDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodSearchRepository: FoodSearchRepository,
    private val foodEntryRepository: FoodEntryRepository,
    private val customFoodRepository: CustomFoodRepository,
    private val savedMealRepository: SavedMealRepository,
) : ViewModel() {

    val targetDate: LocalDate = savedStateHandle.get<String>("date")
        ?.let { LocalDate.parse(it) } ?: LocalDate.now()

    private val _uiState = MutableStateFlow(AddFoodUiState(mealCategory = mealCategoryForHour(LocalTime.now().hour)))
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    private val _pendingDeleteCustomFood = MutableStateFlow<Food?>(null)
    private val _pendingDeleteEntry = MutableStateFlow<FoodEntry?>(null)
    private val _pendingDeleteMeal = MutableStateFlow<SavedMeal?>(null)

    val recentEntries: StateFlow<List<FoodEntry>> = combine(
        foodEntryRepository.recentEntries(),
        _pendingDeleteEntry,
    ) { entries, pending ->
        if (pending != null) entries.filter { it.id != pending.id } else entries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customFoods: StateFlow<List<Food>> = combine(
        customFoodRepository.allFoods(),
        _pendingDeleteCustomFood,
    ) { foods, pending ->
        if (pending != null) foods.filter { it.id != pending.id } else foods
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedMeals: StateFlow<List<SavedMeal>> = combine(
        savedMealRepository.meals(),
        _pendingDeleteMeal,
    ) { meals, pending ->
        if (pending != null) meals.filter { it.id != pending.id } else meals
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Local matches for the query: saved meals first, then custom foods, then the history. */
    val localSearchResults: StateFlow<List<LocalSearchResult>> = combine(
        _uiState.map { it.query },
        customFoodRepository.allFoods(),
        foodEntryRepository.recentEntries(),
        savedMeals,
        combine(_pendingDeleteCustomFood, _pendingDeleteEntry, ::Pair),
    ) { query, customs, entries, meals, (pendingFood, pendingEntry) ->
        if (query.isBlank()) emptyList()
        else {
            val matchedMeals = meals
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { LocalSearchResult.SavedMealResult(it) }
            val filteredCustoms = if (pendingFood != null) customs.filter { it.id != pendingFood.id } else customs
            val filteredEntries = if (pendingEntry != null) entries.filter { it.id != pendingEntry.id } else entries
            val matchedCustoms = filteredCustoms
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { LocalSearchResult.CustomFoodResult(it) }
            val matchedHistory = filteredEntries
                .filter { it.foodName.contains(query, ignoreCase = true) }
                .distinctBy { it.foodName.lowercase() }
                .map { LocalSearchResult.HistoryResult(it) }
            matchedMeals + matchedCustoms + matchedHistory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bumped by [retryRemoteSearch] to send the current query to Open Food Facts once more. */
    private val _remoteRetry = MutableStateFlow(0)

    /**
     * Open Food Facts hits for the current query, below the local results.
     *
     * Local search stays instant and offline; this is the "not in my history yet" case, so it is
     * deliberately lazy: it waits for a pause in typing and for the query to be worth a request. The
     * search service is a shared, rate-limited community resource — one request per keystroke would
     * be both rude and slower than useful.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val remoteSearchResults: StateFlow<RemoteSearchState> = _uiState
        .map { it.query.trim() }
        .distinctUntilChanged()
        .debounce { if (it.length >= MIN_REMOTE_QUERY_LENGTH) SEARCH_DEBOUNCE_MS else 0L }
        // A retry re-emits the same query, which the flatMapLatest below then requests again.
        .combine(_remoteRetry) { query, _ -> query }
        .flatMapLatest { query ->
            if (query.length < MIN_REMOTE_QUERY_LENGTH) {
                flowOf(RemoteSearchState())
            } else {
                flow {
                    emit(RemoteSearchState(isLoading = true))
                    emit(
                        foodSearchRepository.searchByName(query).fold(
                            onSuccess = { RemoteSearchState(results = it) },
                            onFailure = { RemoteSearchState(failed = true) },
                        ),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteSearchState())

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    /** Which result group the search shows; kept across queries until the user picks another. */
    fun onSearchSourceChange(source: SearchSource) = _uiState.update { it.copy(searchSource = source) }

    /** Asks Open Food Facts again after a failed search; a no-op while the query is too short. */
    fun retryRemoteSearch() = _remoteRetry.update { it + 1 }

    fun selectFood(food: Food) = _uiState.update {
        it.copy(selectedFood = food, amountGrams = "100", mealCategory = defaultMealCategory(), tag = food.tag)
    }

    fun selectRecentFood(entry: FoodEntry) {
        val food = FoodPortions.per100g(entry)
        _uiState.update { it.copy(selectedFood = food, amountGrams = entry.amountGrams.formatAmount(), mealCategory = defaultMealCategory(), tag = entry.tag) }
    }

    fun saveCustomFood(food: Food) {
        viewModelScope.launch {
            val saved = customFoodRepository.save(food)
            _uiState.update { it.copy(selectedFood = saved, amountGrams = "100") }
        }
    }

    fun updateCustomFood(food: Food) {
        viewModelScope.launch { customFoodRepository.update(food) }
    }

    // --- custom food deferred delete ---

    fun deletePendingCustomFood(food: Food) {
        _pendingDeleteCustomFood.value = food
    }

    fun confirmDeleteCustomFood(food: Food) {
        if (_pendingDeleteCustomFood.value?.id == food.id) _pendingDeleteCustomFood.value = null
        food.id.toLongOrNull()?.let { id -> viewModelScope.launch { customFoodRepository.delete(id) } }
    }

    fun undoDeleteCustomFood(food: Food) {
        if (_pendingDeleteCustomFood.value?.id == food.id) _pendingDeleteCustomFood.value = null
    }

    // --- history entry deferred delete ---

    fun deletePendingEntry(entry: FoodEntry) {
        _pendingDeleteEntry.value = entry
    }

    fun confirmDeleteEntry(entry: FoodEntry) {
        if (_pendingDeleteEntry.value?.id == entry.id) _pendingDeleteEntry.value = null
        viewModelScope.launch { foodEntryRepository.delete(entry.id) }
    }

    fun undoDeleteEntry(entry: FoodEntry) {
        if (_pendingDeleteEntry.value?.id == entry.id) _pendingDeleteEntry.value = null
    }

    fun dismissSelection() = _uiState.update { it.copy(selectedFood = null) }

    fun onAmountChange(amount: String) = _uiState.update { it.copy(amountGrams = amount) }

    fun onMealCategoryChange(category: MealCategory) = _uiState.update { it.copy(mealCategory = category) }

    fun onTagChange(tag: FoodTag) = _uiState.update { it.copy(tag = tag) }

    fun confirmAdd() {
        val state = _uiState.value
        val food = state.selectedFood ?: return
        val amount = state.amountGrams.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            foodEntryRepository.add(
                FoodPortions.entry(food, amount, state.mealCategory, state.tag, targetDate, System.currentTimeMillis()),
            )
            _uiState.update { it.copy(selectedFood = null, amountGrams = "100", mealCategory = defaultMealCategory(), tag = FoodTag.NONE, entryAdded = true) }
        }
    }

    /**
     * Hands the food in the amount sheet over as a meal ingredient (instead of logging it) and closes
     * the sheet. The query is cleared too, so the next ingredient starts from a fresh search. Null
     * while nothing is selected or the amount isn't a positive number.
     */
    fun takeIngredient(): SavedMealItem? {
        val state = _uiState.value
        val food = state.selectedFood ?: return null
        val amount = state.amountGrams.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        _uiState.update { it.copy(selectedFood = null, amountGrams = "100", tag = FoodTag.NONE, query = "") }
        return SavedMealItem(food.copy(tag = state.tag), amount)
    }

    // --- saved meals: logging ---

    /** Opens the log sheet for [meal], every ingredient pre-filled with its saved amount. */
    fun selectMeal(meal: SavedMeal) = _uiState.update {
        it.copy(
            mealToLog = MealLogDraft(meal, meal.items.map { item -> item.amountGrams.formatAmount() }),
            mealCategory = defaultMealCategory(),
        )
    }

    fun onMealLogAmountChange(index: Int, amount: String) = _uiState.update { state ->
        val draft = state.mealToLog ?: return@update state
        state.copy(mealToLog = draft.copy(amounts = draft.amounts.toMutableList().also { it[index] = amount }))
    }

    fun dismissMealLog() = _uiState.update { it.copy(mealToLog = null) }

    /**
     * Logs the meal as one entry per ingredient. An ingredient whose amount was cleared or set to 0
     * is left out for today ("ohne Milch") — the saved meal itself is not changed.
     */
    fun confirmLogMeal() {
        val state = _uiState.value
        val draft = state.mealToLog ?: return
        val portions = draft.portions()
        if (portions.isEmpty()) return
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            foodEntryRepository.addAll(
                FoodPortions.mealEntries(
                    items = portions.map { it.first },
                    amounts = portions.map { it.second },
                    mealCategory = state.mealCategory,
                    date = targetDate,
                    nowMs = nowMs,
                ),
            )
            savedMealRepository.markUsed(draft.meal.id, nowMs)
            _uiState.update { it.copy(mealToLog = null, mealCategory = defaultMealCategory(), entryAdded = true) }
        }
    }

    // --- saved meals: deferred delete ---

    fun deletePendingMeal(meal: SavedMeal) {
        _pendingDeleteMeal.value = meal
    }

    fun confirmDeleteMeal(meal: SavedMeal) {
        if (_pendingDeleteMeal.value?.id == meal.id) _pendingDeleteMeal.value = null
        viewModelScope.launch { savedMealRepository.delete(meal.id) }
    }

    fun undoDeleteMeal(meal: SavedMeal) {
        if (_pendingDeleteMeal.value?.id == meal.id) _pendingDeleteMeal.value = null
    }

    fun handleBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            foodSearchRepository.getByBarcode(barcode)
                .onSuccess { food ->
                    if (food != null) {
                        _uiState.update { it.copy(isLoading = false, selectedFood = food, amountGrams = "100", tag = food.tag) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = BarcodeError.PRODUCT_NOT_FOUND) }
                    }
                }
                .onFailure { e ->
                    val error = when (e) {
                        is BarcodeException.ServerUnavailable -> BarcodeError.SERVER_UNAVAILABLE
                        is BarcodeException.NetworkUnavailable -> BarcodeError.NETWORK_UNAVAILABLE
                        else -> BarcodeError.UNKNOWN
                    }
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun defaultMealCategory() = mealCategoryForHour(LocalTime.now().hour)

    fun entryAddedHandled() = _uiState.update { it.copy(entryAdded = false) }
}

sealed interface LocalSearchResult {
    data class SavedMealResult(val meal: SavedMeal) : LocalSearchResult
    data class HistoryResult(val entry: FoodEntry) : LocalSearchResult
    data class CustomFoodResult(val food: Food) : LocalSearchResult
}

/**
 * The online-search section's state. [failed] is kept apart from an empty [results] on purpose: "no
 * product found" and "couldn't reach Open Food Facts" call for different messages, and the user can
 * act on the second one.
 */
data class RemoteSearchState(
    val results: List<Food> = emptyList(),
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

/** Barcode-lookup failure kinds; the UI resolves each to a localized message. */
enum class BarcodeError {
    PRODUCT_NOT_FOUND,
    SERVER_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}

data class AddFoodUiState(
    val query: String = "",
    val searchSource: SearchSource = SearchSource.ALL,
    val isLoading: Boolean = false,
    val error: BarcodeError? = null,
    val selectedFood: Food? = null,
    val amountGrams: String = "100",
    val mealCategory: MealCategory = MealCategory.SNACK,
    val tag: FoodTag = FoodTag.NONE,
    val entryAdded: Boolean = false,
    val mealToLog: MealLogDraft? = null,
)

/** A saved meal about to be logged: one editable amount (as typed) per ingredient, same order. */
data class MealLogDraft(
    val meal: SavedMeal,
    val amounts: List<String>,
) {
    /** The ingredients to log with their parsed amounts — cleared or non-positive ones left out. */
    fun portions(): List<Pair<SavedMealItem, Double>> = meal.items.zip(amounts).mapNotNull { (item, raw) ->
        raw.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 }?.let { item to it }
    }
}

/** An amount as an input-field string: whole grams without a trailing ".0". */
internal fun Double.formatAmount(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

/** Below this many characters a query is too vague to be worth a request. */
private const val MIN_REMOTE_QUERY_LENGTH = 3

/** Typing pause before the request goes out. */
private const val SEARCH_DEBOUNCE_MS = 450L

internal fun mealCategoryForHour(hour: Int): MealCategory = when {
    hour in 5..9   -> MealCategory.BREAKFAST
    hour in 10..13 -> MealCategory.LUNCH
    hour in 17..21 -> MealCategory.DINNER
    else           -> MealCategory.SNACK
}
