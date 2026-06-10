package dev.antonlammers.macrotrac.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val foodEntryRepository: FoodEntryRepository,
    private val goalRepository: GoalRepository,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    private val _pendingDelete = MutableStateFlow<FoodEntry?>(null)

    val uiState: StateFlow<OverviewUiState> = _date
        .flatMapLatest { date ->
            combine(
                foodEntryRepository.entriesForDate(date),
                foodEntryRepository.entriesForDate(date.minusDays(1)),
                goalRepository.goal(),
                weightRepository.entryForDate(date),
                _pendingDelete,
            ) { entries, previousEntries, goal, weight, pending ->
                OverviewUiState(
                    entries = if (pending != null) entries.filter { it.id != pending.id } else entries,
                    previousDayEntries = previousEntries,
                    goal = goal,
                    date = date,
                    todayWeight = weight,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OverviewUiState(),
        )

    fun previousDay() = _date.update { it.minusDays(1) }
    fun nextDay() = _date.update { it.plusDays(1) }
    fun goToToday() = _date.update { LocalDate.now() }

    fun update(entry: FoodEntry) {
        viewModelScope.launch { foodEntryRepository.update(entry) }
    }

    fun deletePending(entry: FoodEntry) {
        _pendingDelete.value = entry
    }

    fun confirmDelete(entry: FoodEntry) {
        if (_pendingDelete.value?.id == entry.id) _pendingDelete.value = null
        viewModelScope.launch { foodEntryRepository.delete(entry.id) }
    }

    fun undoDelete(entry: FoodEntry) {
        if (_pendingDelete.value?.id == entry.id) _pendingDelete.value = null
    }

    /**
     * Copies every entry of [category] from the previous day into the currently viewed day,
     * keeping the same foods, amounts and meal category. No-op if there is nothing to copy.
     */
    fun copyMealFromPreviousDay(category: MealCategory) {
        val state = uiState.value
        val toCopy = state.previousDayEntries.filter { it.mealCategory == category }
        if (toCopy.isEmpty()) return
        val targetDate = state.date
        viewModelScope.launch {
            toCopy.forEach { entry ->
                foodEntryRepository.add(
                    entry.copy(
                        id = 0,
                        date = targetDate,
                        timestampMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun saveWeight(weightKg: Double) {
        viewModelScope.launch {
            weightRepository.save(
                WeightEntry(
                    weightKg = weightKg,
                    date = _date.value,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }
}

data class OverviewUiState(
    val entries: List<FoodEntry> = emptyList(),
    val previousDayEntries: List<FoodEntry> = emptyList(),
    val goal: DailyGoal = DailyGoal(),
    val date: LocalDate = LocalDate.now(),
    val todayWeight: WeightEntry? = null,
) {
    val totalKcal get() = entries.sumOf { it.kcal }
    val totalProtein get() = entries.sumOf { it.proteinG }
    val totalCarbs get() = entries.sumOf { it.carbsG }
    val totalFat get() = entries.sumOf { it.fatG }
    val totalSugar get() = entries.sumOf { it.sugarG }
    val totalFiber get() = entries.sumOf { it.fiberG }
    val totalSalt get() = entries.sumOf { it.saltG }

    fun entriesForMeal(category: MealCategory): List<FoodEntry> =
        entries.filter { it.mealCategory == category }

    fun kcalForMeal(category: MealCategory): Double =
        entriesForMeal(category).sumOf { it.kcal }

    /**
     * Main meals that have no entry on the viewed day but do have entries on the previous day —
     * i.e. the meals for which a "copy from yesterday" button should be offered.
     */
    val copyableMeals: Set<MealCategory>
        get() = MealCategory.mainMeals.filter { meal ->
            entries.none { it.mealCategory == meal } &&
                previousDayEntries.any { it.mealCategory == meal }
        }.toSet()
}
