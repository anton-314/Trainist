package dev.antonlammers.trainist.ui.addfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.model.BarcodeException
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.repository.CustomFoodRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.FoodSearchRepository
import dev.antonlammers.trainist.util.normalizeDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

    val targetDate: LocalDate = savedStateHandle.get<String>("date")
        ?.let { LocalDate.parse(it) } ?: LocalDate.now()

    private val _uiState = MutableStateFlow(AddFoodUiState(mealCategory = mealCategoryForHour(LocalTime.now().hour)))
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    private val _pendingDeleteCustomFood = MutableStateFlow<Food?>(null)
    private val _pendingDeleteEntry = MutableStateFlow<FoodEntry?>(null)

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

    val localSearchResults: StateFlow<List<LocalSearchResult>> = combine(
        _uiState.map { it.query },
        customFoodRepository.allFoods(),
        foodEntryRepository.recentEntries(),
        _pendingDeleteCustomFood,
        _pendingDeleteEntry,
    ) { query, customs, entries, pendingFood, pendingEntry ->
        if (query.isBlank()) emptyList()
        else {
            val filteredCustoms = if (pendingFood != null) customs.filter { it.id != pendingFood.id } else customs
            val filteredEntries = if (pendingEntry != null) entries.filter { it.id != pendingEntry.id } else entries
            val matchedCustoms = filteredCustoms
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { LocalSearchResult.CustomFoodResult(it) }
            val matchedHistory = filteredEntries
                .filter { it.foodName.contains(query, ignoreCase = true) }
                .distinctBy { it.foodName.lowercase() }
                .map { LocalSearchResult.HistoryResult(it) }
            matchedCustoms + matchedHistory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun selectFood(food: Food) = _uiState.update {
        it.copy(selectedFood = food, amountGrams = "100", mealCategory = defaultMealCategory(), tag = food.tag)
    }

    fun selectRecentFood(entry: FoodEntry) {
        val factor = if (entry.amountGrams > 0) 100.0 / entry.amountGrams else 1.0
        val food = Food(
            id = entry.foodName,
            name = entry.foodName,
            brand = entry.brand,
            kcalPer100g = entry.kcal * factor,
            proteinPer100g = entry.proteinG * factor,
            carbsPer100g = entry.carbsG * factor,
            fatPer100g = entry.fatG * factor,
            sugarPer100g = entry.sugarG * factor,
            fiberPer100g = entry.fiberG * factor,
            saltPer100g = entry.saltG * factor,
            tag = entry.tag,
        )
        val prevAmount = if (entry.amountGrams % 1.0 == 0.0) entry.amountGrams.toInt().toString()
                         else entry.amountGrams.toString()
        _uiState.update { it.copy(selectedFood = food, amountGrams = prevAmount, mealCategory = defaultMealCategory(), tag = entry.tag) }
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
        val factor = amount / 100.0
        viewModelScope.launch {
            foodEntryRepository.add(
                FoodEntry(
                    foodName = food.name,
                    brand = food.brand,
                    amountGrams = amount,
                    kcal = food.kcalPer100g * factor,
                    proteinG = food.proteinPer100g * factor,
                    carbsG = food.carbsPer100g * factor,
                    fatG = food.fatPer100g * factor,
                    sugarG = food.sugarPer100g * factor,
                    fiberG = food.fiberPer100g * factor,
                    saltG = food.saltPer100g * factor,
                    mealCategory = state.mealCategory,
                    tag = state.tag,
                    date = targetDate,
                    timestampMs = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(selectedFood = null, amountGrams = "100", mealCategory = defaultMealCategory(), tag = FoodTag.NONE, entryAdded = true) }
        }
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
    data class HistoryResult(val entry: FoodEntry) : LocalSearchResult
    data class CustomFoodResult(val food: Food) : LocalSearchResult
}

/** Barcode-lookup failure kinds; the UI resolves each to a localized message. */
enum class BarcodeError {
    PRODUCT_NOT_FOUND,
    SERVER_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}

data class AddFoodUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: BarcodeError? = null,
    val selectedFood: Food? = null,
    val amountGrams: String = "100",
    val mealCategory: MealCategory = MealCategory.SNACK,
    val tag: FoodTag = FoodTag.NONE,
    val entryAdded: Boolean = false,
)

internal fun mealCategoryForHour(hour: Int): MealCategory = when {
    hour in 5..9   -> MealCategory.BREAKFAST
    hour in 10..13 -> MealCategory.LUNCH
    hour in 17..21 -> MealCategory.DINNER
    else           -> MealCategory.SNACK
}
