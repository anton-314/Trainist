package dev.antonlammers.trainist.ui.addfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.FoodPortions
import dev.antonlammers.trainist.domain.NutritionTotals
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.SavedMealRepository
import dev.antonlammers.trainist.util.normalizeDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Creating and editing a saved meal ("Meine Mahlzeiten") on the AddFood screen.
 *
 * The draft lives here rather than in the sheet's composition because building a meal leaves the
 * sheet: ingredients are picked in the screen's ordinary search ([startPicking]), so history, custom
 * foods, Open Food Facts and the barcode scanner all work for them without a second search UI. The
 * sheet hides while [MealEditorUiState.picking] and the draft is waiting for it when it comes back.
 *
 * Three ways in, told apart by [MealDraft.origin]: the "+" sheet's meal mode ([startNew]), a swipe on
 * an existing meal ([edit]), and the Overview's "save as meal" — the AddFood route carries a
 * `saveMeal` category, and the draft starts out with that day's entries of the category.
 */
@HiltViewModel
class MealEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savedMealRepository: SavedMealRepository,
    private val foodEntryRepository: FoodEntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealEditorUiState())
    val uiState: StateFlow<MealEditorUiState> = _uiState.asStateFlow()

    /** Synthetic per-draft row keys, so a list row keeps its identity when another is removed. */
    private var nextKey = 1L

    init {
        val category = savedStateHandle.get<String>(SAVE_MEAL_ARG)
            ?.let { raw -> MealCategory.entries.firstOrNull { it.name == raw } }
        val date = savedStateHandle.get<String>("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (category != null && date != null) startFromDay(date, category)
    }

    fun startNew() {
        if (_uiState.value.draft != null) return
        _uiState.update { it.copy(draft = MealDraft(origin = MealDraftOrigin.NEW)) }
    }

    fun edit(meal: SavedMeal) = _uiState.update {
        it.copy(
            draft = MealDraft(
                origin = MealDraftOrigin.EDIT,
                existing = meal,
                name = meal.name,
                items = meal.items.map { item -> item.toDraftItem() },
            ),
            picking = false,
        )
    }

    private fun startFromDay(date: LocalDate, category: MealCategory) {
        viewModelScope.launch {
            val items = foodEntryRepository.entriesForDate(date).first()
                .filter { it.mealCategory == category }
                .sortedBy { it.timestampMs }
                .map { SavedMealItem(FoodPortions.per100g(it), it.amountGrams).toDraftItem() }
            _uiState.update { it.copy(draft = MealDraft(origin = MealDraftOrigin.FROM_DAY, items = items)) }
        }
    }

    fun onNameChange(name: String) = updateDraft { it.copy(name = name) }

    fun onAmountChange(key: Long, amount: String) = updateDraft { draft ->
        draft.copy(items = draft.items.map { if (it.key == key) it.copy(amount = amount) else it })
    }

    fun removeItem(key: Long) = updateDraft { draft -> draft.copy(items = draft.items.filterNot { it.key == key }) }

    /** Leaves the sheet for the screen's search; the next picked food comes back via [addItem]. */
    fun startPicking() {
        if (_uiState.value.draft == null) return
        _uiState.update { it.copy(picking = true) }
    }

    fun cancelPicking() = _uiState.update { it.copy(picking = false) }

    fun addItem(item: SavedMealItem) = _uiState.update { state ->
        val draft = state.draft ?: return@update state.copy(picking = false)
        state.copy(draft = draft.copy(items = draft.items + item.toDraftItem()), picking = false)
    }

    /** Drops the draft — the sheet was dismissed. */
    fun discard() = _uiState.update { MealEditorUiState() }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val meal = draft.toMeal() ?: return
        viewModelScope.launch {
            val id = savedMealRepository.save(meal)
            _uiState.update { MealEditorUiState(saved = MealSaved(meal.copy(id = id), draft.origin)) }
        }
    }

    fun savedHandled() = _uiState.update { it.copy(saved = null) }

    private fun updateDraft(transform: (MealDraft) -> MealDraft) = _uiState.update { state ->
        state.draft?.let { state.copy(draft = transform(it)) } ?: state
    }

    private fun SavedMealItem.toDraftItem() = MealDraftItem(nextKey++, food, amountGrams.formatAmount())

    companion object {
        /** AddFood route argument naming the meal category to turn into a saved meal. */
        const val SAVE_MEAL_ARG = "saveMeal"
    }
}

data class MealEditorUiState(
    /** The meal being built; null while no meal sheet is open. */
    val draft: MealDraft? = null,
    /** The sheet is hidden while the user picks an ingredient in the search. */
    val picking: Boolean = false,
    /** One-shot: a meal was just saved — the screen decides what follows from its origin. */
    val saved: MealSaved? = null,
)

enum class MealDraftOrigin {
    /** Created from scratch via the "+" sheet's meal mode. */
    NEW,

    /** An existing meal opened for editing. */
    EDIT,

    /** Started from a meal section on the Overview, whose entries are already logged. */
    FROM_DAY,
}

data class MealDraft(
    val origin: MealDraftOrigin,
    val existing: SavedMeal? = null,
    val name: String = "",
    val items: List<MealDraftItem> = emptyList(),
) {
    private val parsedItems: List<SavedMealItem?> = items.map { item ->
        item.amount.normalizeDecimal().toDoubleOrNull()?.takeIf { it > 0 }?.let { SavedMealItem(item.food, it) }
    }

    /** A name, at least one ingredient, and a positive amount for every ingredient. */
    val canSave: Boolean = name.isNotBlank() && items.isNotEmpty() && parsedItems.all { it != null }

    /** Totals over the ingredients whose amount currently parses. */
    val totals: NutritionTotals = FoodPortions.totals(parsedItems.filterNotNull())

    /** The meal to store; keeps the stable key of the meal being edited. Null unless [canSave]. */
    fun toMeal(): SavedMeal? {
        if (!canSave) return null
        return SavedMeal(
            id = existing?.id ?: 0,
            stableId = existing?.stableId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            items = parsedItems.filterNotNull(),
        )
    }
}

data class MealDraftItem(
    val key: Long,
    val food: Food,
    val amount: String,
)

data class MealSaved(val meal: SavedMeal, val origin: MealDraftOrigin)
