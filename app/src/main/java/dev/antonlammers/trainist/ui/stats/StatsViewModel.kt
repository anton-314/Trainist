package dev.antonlammers.trainist.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.WorkoutMetrics
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// Display labels live in the UI layer (TimeRange.label() in StatsScreen.kt) since this ViewModel
// has no Compose context to resolve a string resource.
enum class TimeRange {
    WEEK,
    MONTH,
    YEAR,
}

data class ChartPoint(val label: String, val value: Double)

data class StatsUiState(
    val timeRange: TimeRange = TimeRange.WEEK,
    val caloriePoints: List<ChartPoint> = emptyList(),
    /** Share of clean (healthy) kcal per bucket, in percent (0–100). */
    val cleanPoints: List<ChartPoint> = emptyList(),
    /** Clean share over the whole range (total healthy kcal / total kcal), or `null` if no entries. */
    val overallCleanPercent: Int? = null,
    val weight: WeightChartData = WeightChartData(),
    val goalKcal: Double = 0.0,
    /** Completed sessions per time bucket (training frequency). */
    val frequencyPoints: List<ChartPoint> = emptyList(),
    /** Exercises trained in range, selectable for the strength chart. */
    val strengthExercises: List<ExerciseOption> = emptyList(),
    /** The exercise the strength chart is showing (defaults to the first option). */
    val selectedExerciseId: String? = null,
    val strength: StrengthChartData = StrengthChartData(),
    /** User-customizable order of the chart cards (drag-to-reorder). */
    val cardOrder: List<StatCardType> = StatCardType.DEFAULT_ORDER,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val exerciseCatalogRepository: ExerciseCatalogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)
    private val _selectedExerciseId = MutableStateFlow<String?>(null)
    private val _cardOrder = MutableStateFlow(StatCardType.DEFAULT_ORDER)

    // All weigh-ins (loaded once) — used to resolve body weight for bodyweight-exercise 1RMs, which
    // may reference a weigh-in from before the visible range's start ("last known" fallback).
    private val _allWeights = MutableStateFlow<List<WeightEntry>>(emptyList())

    init {
        viewModelScope.launch { _allWeights.value = weightRepository.allEntries() }
        viewModelScope.launch { _cardOrder.value = settingsRepository.statsCardOrder() }
    }

    private val chartState: StateFlow<StatsUiState> =
        combine(_timeRange, _selectedExerciseId) { range, selectedId -> range to selectedId }
        .flatMapLatest { (range, selectedId) ->
            val (from, to) = range.dateRange()
            combine(
                foodEntryRepository.entriesInRange(from, to),
                weightRepository.entriesInRange(from, to),
                goalRepository.goal(),
                workoutSessionRepository.sessions(),
                combine(exerciseCatalogRepository.exercises(), _allWeights) { catalog, allWeights -> catalog to allWeights },
            ) { foodEntries, weightEntries, goal, allSessions, catalogAndWeights ->
                val (catalog, allWeights) = catalogAndWeights
                val byStableId = catalog.associateBy { it.stableId }
                val sessionsInRange = allSessions.filter {
                    !it.isActive && !it.date.isBefore(from) && !it.date.isAfter(to)
                }
                val options = sessionsInRange
                    .flatMap { session -> session.exercises.map { it.exerciseStableId } }
                    .distinct()
                    .map { id -> ExerciseOption(id, byStableId[id]?.name ?: id) }
                    .sortedBy { it.name.lowercase() }
                val effectiveId = selectedId?.takeIf { id -> options.any { it.stableId == id } }
                    ?: options.firstOrNull()?.stableId
                val strength = effectiveId?.let { id ->
                    val samples = WorkoutSeries.strengthSamples(
                        range, sessionsInRange, id,
                        typeOf = { byStableId[it]?.type ?: ExerciseType.WEIGHT_REPS },
                        bodyWeightForDate = { WorkoutMetrics.resolveBodyWeightKg(allWeights, it) },
                    )
                    val (minKg, maxKg) = WorkoutSeries.bounds(samples)
                    StrengthChartData(samples, from, to, minKg, maxKg)
                } ?: StrengthChartData(rangeStart = from, rangeEnd = to)

                StatsUiState(
                    timeRange = range,
                    caloriePoints = bucketedPoints(range, from, to, foodEntries) { it.sumOf { e -> e.kcal } },
                    cleanPoints = bucketedPoints(range, from, to, foodEntries) { cleanPercent(it) },
                    overallCleanPercent = cleanPercent(foodEntries).takeIf { foodEntries.isNotEmpty() }?.let { Math.round(it).toInt() },
                    weight = buildWeightData(range, from, to, weightEntries, goal.targetWeightKg),
                    goalKcal = goal.kcal,
                    frequencyPoints = WorkoutSeries.frequency(range, from, to, sessionsInRange.map { it.date }),
                    strengthExercises = options,
                    selectedExerciseId = effectiveId,
                    strength = strength,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    // Recombined on top of chartState so a reorder never re-triggers the (expensive) repository
    // re-subscription above — only the card order itself changes.
    val uiState: StateFlow<StatsUiState> = combine(chartState, _cardOrder) { state, order ->
        state.copy(cardOrder = order)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun setTimeRange(range: TimeRange) = _timeRange.update { range }

    fun setSelectedExercise(stableId: String) = _selectedExerciseId.update { stableId }

    /** Swap two cards — called repeatedly (once per adjacent step) while a card is dragged into place. */
    fun moveCard(from: Int, to: Int) {
        val order = _cardOrder.value
        if (from !in order.indices || to !in order.indices) return
        val reordered = order.toMutableList().apply { val tmp = this[from]; this[from] = this[to]; this[to] = tmp }
        _cardOrder.value = reordered
        viewModelScope.launch { settingsRepository.setStatsCardOrder(reordered) }
    }

    /**
     * Buckets [entries] over [range] (per day for WEEK/MONTH, per month for YEAR) and maps each
     * bucket's entries to a value via [valueOf]. Empty buckets yield 0.0. Shared by the calorie and
     * clean-eating charts so both stay aligned on the same time axis.
     */
    private fun bucketedPoints(
        range: TimeRange,
        from: LocalDate,
        to: LocalDate,
        entries: List<FoodEntry>,
        valueOf: (List<FoodEntry>) -> Double,
    ): List<ChartPoint> {
        return when (range) {
            TimeRange.WEEK, TimeRange.MONTH -> {
                val fmt = if (range == TimeRange.WEEK)
                    localizedDateFormatter("EE")
                else
                    localizedDateFormatter("d")
                val byDate = entries.groupBy { it.date }
                generateSequence(from) { d -> if (d < to) d.plusDays(1) else null }
                    .map { date -> ChartPoint(date.format(fmt), valueOf(byDate[date].orEmpty())) }
                    .toList()
            }
            TimeRange.YEAR -> {
                val fmt = localizedDateFormatter("MMM")
                val byMonth = entries.groupBy { YearMonth.from(it.date) }
                val fromMonth = YearMonth.from(from)
                val toMonth = YearMonth.from(to)
                generateSequence(fromMonth) { m -> if (m < toMonth) m.plusMonths(1) else null }
                    .map { month -> ChartPoint(month.format(fmt), valueOf(byMonth[month].orEmpty())) }
                    .toList()
            }
        }
    }

    /** Clean share of a set of entries in percent (0–100): weighted clean kcal / total kcal; 0 if empty. */
    private fun cleanPercent(entries: List<FoodEntry>): Double {
        val total = entries.sumOf { it.kcal }
        if (total <= 0.0) return 0.0
        val clean = entries.sumOf { it.kcal * it.tag.cleanWeight }
        return clean / total * 100.0
    }

    private fun buildWeightData(
        range: TimeRange,
        from: LocalDate,
        to: LocalDate,
        entries: List<WeightEntry>,
        targetKg: Double?,
    ): WeightChartData {
        val samples = WeightSeries.samples(range, entries)
        val trend = WeightSeries.movingAverage(samples, WeightSeries.trendWindowDays(range))
        val (minKg, maxKg) = WeightSeries.bounds(samples, targetKg)
        return WeightChartData(
            samples = samples,
            trend = trend,
            rangeStart = from,
            rangeEnd = to,
            minKg = minKg,
            maxKg = maxKg,
            targetKg = targetKg,
        )
    }
}

private fun TimeRange.dateRange(): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    val from = when (this) {
        TimeRange.WEEK -> today.minusDays(6)
        TimeRange.MONTH -> today.minusDays(29)
        TimeRange.YEAR -> today.minusMonths(11).withDayOfMonth(1)
    }
    return from to today
}
