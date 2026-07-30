package dev.antonlammers.trainist.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.ProgressionAdvice
import dev.antonlammers.trainist.domain.ProgressionAdvisor
import dev.antonlammers.trainist.domain.ProgressionFacts
import dev.antonlammers.trainist.domain.ProgressionTrend
import dev.antonlammers.trainist.domain.WorkoutMetrics
import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.repository.BodyMeasurementRepository
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/** State of the Körpermaße card: which [MeasurementType] is selected plus its chart data. */
data class MeasurementCardState(
    val selectedType: MeasurementType = MeasurementType.WAIST,
    val chart: MeasurementChartData = MeasurementChartData(),
)

/**
 * State of the progressive-overload card: the overall strength index plus the advisor's verdict and
 * tips. Unlike every other card this one runs on its own fixed [OverloadSeries.WINDOW_WEEKS]-week
 * window and ignores the screen's time-range chips — a trend needs a minimum observation window, and
 * a 7-day view of it would only ever show noise.
 */
data class OverloadCardState(
    val chart: OverloadChartData = OverloadChartData(),
    val trend: ProgressionTrend = ProgressionTrend.INSUFFICIENT_DATA,
    val advice: List<ProgressionAdvice> = listOf(ProgressionAdvice.COLLECT_MORE_DATA),
    /** Index change over the window in percent; `null` while there is not enough data. */
    val changePercent: Double? = null,
    /** Weeks in the window that produced usable training data. */
    val trainingWeeks: Int = 0,
    /** Completed sessions in the window. */
    val sessions: Int = 0,
)

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
    /** Overall progressive-overload analysis — on its own fixed window, see [OverloadCardState]. */
    val overload: OverloadCardState = OverloadCardState(),
    /** Körpermaße card — selected type + its chart data over the current time range. */
    val measurement: MeasurementCardState = MeasurementCardState(),
    /** Today's already-logged measurements, keyed by type — prefills the quick-add entry sheet. */
    val todaysMeasurements: Map<MeasurementType, Double> = emptyMap(),
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
    private val bodyMeasurementRepository: BodyMeasurementRepository,
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)
    private val _selectedExerciseId = MutableStateFlow<String?>(null)
    private val _selectedMeasurementType = MutableStateFlow(MeasurementType.WAIST)
    private val _cardOrder = MutableStateFlow(StatCardType.DEFAULT_ORDER)

    // All weigh-ins (loaded once) — used to resolve body weight for bodyweight-exercise 1RMs, which
    // may reference a weigh-in from before the visible range's start ("last known" fallback).
    private val _allWeights = MutableStateFlow<List<WeightEntry>>(emptyList())

    init {
        viewModelScope.launch { _allWeights.value = weightRepository.allEntries() }
        viewModelScope.launch { _cardOrder.value = settingsRepository.statsCardOrder() }
    }

    private val chartState: StateFlow<StatsUiState> =
        combine(_timeRange, _selectedExerciseId, _selectedMeasurementType) { range, selectedId, selectedType -> Triple(range, selectedId, selectedType) }
        .flatMapLatest { (range, selectedId, selectedType) ->
            val (from, to) = range.dateRange()
            combine(
                foodEntryRepository.entriesInRange(from, to),
                weightRepository.entriesInRange(from, to),
                goalRepository.goal(),
                workoutSessionRepository.sessions(),
                combine(
                    exerciseCatalogRepository.exercises(),
                    _allWeights,
                    bodyMeasurementRepository.entriesInRange(from, to),
                ) { catalog, allWeights, measurements -> Triple(catalog, allWeights, measurements) },
            ) { foodEntries, weightEntries, goal, allSessions, catalogWeightsMeasurements ->
                val (catalog, allWeights, measurementEntries) = catalogWeightsMeasurements
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
                    measurement = MeasurementCardState(
                        selectedType = selectedType,
                        chart = buildMeasurementData(range, from, to, measurementEntries, selectedType),
                    ),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    // The progressive-overload card runs on its own fixed window, so it lives in a separate combine
    // rather than inside chartState above — switching the time range must not recompute it, and its
    // nutrition slice covers the analysis window, not the selected range.
    private val overloadState: Flow<OverloadCardState> = run {
        val today = LocalDate.now()
        val from = OverloadSeries.windowStart(today)
        combine(
            workoutSessionRepository.sessions(),
            exerciseCatalogRepository.exercises(),
            _allWeights,
            foodEntryRepository.entriesInRange(from, today),
            goalRepository.goal(),
        ) { sessions, catalog, allWeights, foodEntries, goal ->
            buildOverload(from, today, sessions, catalog, allWeights, foodEntries, goal.kcal)
        }
    }

    // Today's measurements for the entry sheet's prefill — always "today", independent of the
    // selected time range, so it lives in its own flow rather than inside chartState above.
    private val todaysMeasurements: Flow<Map<MeasurementType, Double>> =
        bodyMeasurementRepository.entriesForDate(LocalDate.now())
            .map { entries -> entries.associate { it.type to it.valueCm } }

    // Recombined on top of chartState so a reorder never re-triggers the (expensive) repository
    // re-subscription above — only the card order itself changes.
    val uiState: StateFlow<StatsUiState> = combine(chartState, _cardOrder, overloadState, todaysMeasurements) { state, order, overload, todays ->
        state.copy(cardOrder = order, overload = overload, todaysMeasurements = todays)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun setTimeRange(range: TimeRange) = _timeRange.update { range }

    fun setSelectedExercise(stableId: String) = _selectedExerciseId.update { stableId }

    fun setSelectedMeasurementType(type: MeasurementType) = _selectedMeasurementType.update { type }

    /** Saves the entry sheet's values for today; a `null` value clears any existing entry for that type. */
    fun saveMeasurements(values: Map<MeasurementType, Double?>) {
        viewModelScope.launch { bodyMeasurementRepository.save(LocalDate.now(), values) }
    }

    /** Swap two cards — called repeatedly (once per adjacent step) while a card is dragged into place. */
    fun moveCard(from: Int, to: Int) {
        val order = _cardOrder.value
        if (from !in order.indices || to !in order.indices) return
        val reordered = order.toMutableList().apply { val tmp = this[from]; this[from] = this[to]; this[to] = tmp }
        _cardOrder.value = reordered
        viewModelScope.launch { settingsRepository.setStatsCardOrder(reordered) }
    }

    /**
     * Builds the progressive-overload card: the chain-linked overall strength index over the window
     * plus the [ProgressionAdvisor]'s verdict on it. Everything the advisor sees is measured here —
     * the strength trend from the sessions, the energy signal from the *body-weight trend* (with
     * logged kcal only as a fallback), protein per kg from the logged days, and training frequency
     * across the trained span, so a stagnation is attributed to a cause instead of guessed at.
     */
    private fun buildOverload(
        from: LocalDate,
        today: LocalDate,
        allSessions: List<WorkoutSession>,
        catalog: List<Exercise>,
        allWeights: List<WeightEntry>,
        foodEntries: List<FoodEntry>,
        goalKcal: Double,
    ): OverloadCardState {
        val byStableId = catalog.associateBy { it.stableId }
        val sessionsInWindow = allSessions.filter {
            !it.isActive && !it.date.isBefore(from) && !it.date.isAfter(today)
        }
        val index = OverloadSeries.index(
            sessions = sessionsInWindow,
            typeOf = { byStableId[it]?.type ?: ExerciseType.WEIGHT_REPS },
            // Resolved from *all* weigh-ins so a bodyweight 1RM can reference one from before the
            // window start (same rule as the strength chart).
            bodyWeightForDate = { WorkoutMetrics.resolveBodyWeightKg(allWeights, it) },
        )
        val (minIndex, maxIndex) = OverloadSeries.bounds(index.points)
        val spanWeeks = OverloadSeries.spanWeeks(index.points)
        val facts = ProgressionFacts(
            trainingWeeks = index.points.size,
            sessions = sessionsInWindow.size,
            totalChangePercent = OverloadSeries.totalChangePercent(index.points),
            recentChangePercent = OverloadSeries.changeSince(index.points, today.minusWeeks(OverloadSeries.RECENT_WEEKS)),
            bodyWeightTrendKgPerWeek = OverloadSeries.bodyWeightTrendKgPerWeek(
                allWeights.filter { !it.date.isBefore(from) && !it.date.isAfter(today) },
            ),
            kcalVsGoal = OverloadSeries.kcalVsGoal(foodEntries, goalKcal),
            proteinGPerKgBodyWeight = OverloadSeries.proteinPerKgBodyWeight(
                foodEntries,
                WorkoutMetrics.resolveBodyWeightKg(allWeights, today),
            ),
            sessionsPerWeek = if (spanWeeks > 0) sessionsInWindow.size.toDouble() / spanWeeks else 0.0,
        )
        val insight = ProgressionAdvisor.evaluate(facts)
        return OverloadCardState(
            chart = OverloadChartData(
                points = index.points,
                rangeStart = from,
                rangeEnd = today,
                minIndex = minIndex,
                maxIndex = maxIndex,
                trackedExercises = index.trackedExercises,
            ),
            trend = insight.trend,
            advice = insight.advice,
            // Only surfaced once the advisor considers the window large enough to interpret.
            changePercent = facts.totalChangePercent.takeIf { insight.trend != ProgressionTrend.INSUFFICIENT_DATA },
            trainingWeeks = facts.trainingWeeks,
            sessions = facts.sessions,
        )
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

    private fun buildMeasurementData(
        range: TimeRange,
        from: LocalDate,
        to: LocalDate,
        entries: List<BodyMeasurementEntry>,
        type: MeasurementType,
    ): MeasurementChartData {
        val samples = MeasurementSeries.samples(range, entries, type)
        val (minCm, maxCm) = MeasurementSeries.bounds(samples)
        return MeasurementChartData(samples = samples, rangeStart = from, rangeEnd = to, minCm = minCm, maxCm = maxCm)
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
