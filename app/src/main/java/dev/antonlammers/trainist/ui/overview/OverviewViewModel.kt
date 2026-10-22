package dev.antonlammers.trainist.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
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
            val week = weekOf(date)
            combine(
                combine(
                    foodEntryRepository.entriesForDate(date),
                    foodEntryRepository.entriesForDate(date.minusDays(1)),
                    foodEntryRepository.entriesInRange(week.first(), week.last()),
                    ::Triple,
                ),
                goalRepository.goal(),
                weightRepository.entryForDate(date),
                _pendingDelete,
            ) { (entries, previousEntries, weekEntries), goal, weight, pending ->
                OverviewUiState(
                    entries = if (pending != null) entries.filter { it.id != pending.id } else entries,
                    previousDayEntries = previousEntries,
                    goal = goal,
                    date = date,
                    todayWeight = weight,
                    // An entry pending deletion already left the day's list; the strip agrees.
                    week = weekSummary(date, if (pending != null) weekEntries.filter { it.id != pending.id } else weekEntries),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OverviewUiState(),
        )

    fun selectDate(date: LocalDate) {
        _date.value = date
    }

    /** Moves the week strip by [weeks], keeping the weekday: Wednesday stays Wednesday. */
    fun shiftWeek(weeks: Long) = _date.update { it.plusWeeks(weeks) }

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
    /** The ISO week around [date], Monday first — drives the week strip. */
    val week: List<DaySummary> = weekSummary(date, emptyList()),
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

    /** Consumed kcal attributed to each [FoodTag] — drives the segmented calorie bar. */
    fun kcalForTag(tag: FoodTag): Double =
        entries.filter { it.tag == tag }.sumOf { it.kcal }

    /** kcal-weighted clean contribution ([FoodTag.HEALTHY] counts fully, [FoodTag.NEUTRAL] half). */
    val cleanKcal: Double get() = entries.sumOf { it.kcal * it.tag.cleanWeight }

    /**
     * Share of consumed kcal that is clean, in percent (0–100), or `null` when nothing is logged.
     * Untagged and unhealthy kcal count against it, so the target of 80–90% rewards tagging.
     */
    val cleanPercent: Int?
        get() = if (totalKcal > 0) Math.round(cleanKcal / totalKcal * 100).toInt() else null

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
