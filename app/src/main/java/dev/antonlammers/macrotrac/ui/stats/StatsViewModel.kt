package dev.antonlammers.macrotrac.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.FoodEntry
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class TimeRange(val label: String) {
    WEEK("7 Tage"),
    MONTH("30 Tage"),
    YEAR("1 Jahr"),
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
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)

    val uiState: StateFlow<StatsUiState> = _timeRange
        .flatMapLatest { range ->
            val (from, to) = range.dateRange()
            combine(
                foodEntryRepository.entriesInRange(from, to),
                weightRepository.entriesInRange(from, to),
                goalRepository.goal(),
            ) { foodEntries, weightEntries, goal ->
                StatsUiState(
                    timeRange = range,
                    caloriePoints = bucketedPoints(range, from, to, foodEntries) { it.sumOf { e -> e.kcal } },
                    cleanPoints = bucketedPoints(range, from, to, foodEntries) { cleanPercent(it) },
                    overallCleanPercent = cleanPercent(foodEntries).takeIf { foodEntries.isNotEmpty() }?.let { Math.round(it).toInt() },
                    weight = buildWeightData(range, from, to, weightEntries, goal.targetWeightKg),
                    goalKcal = goal.kcal,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    fun setTimeRange(range: TimeRange) = _timeRange.update { range }

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
                    DateTimeFormatter.ofPattern("EE", Locale("de"))
                else
                    DateTimeFormatter.ofPattern("d", Locale("de"))
                val byDate = entries.groupBy { it.date }
                generateSequence(from) { d -> if (d < to) d.plusDays(1) else null }
                    .map { date -> ChartPoint(date.format(fmt), valueOf(byDate[date].orEmpty())) }
                    .toList()
            }
            TimeRange.YEAR -> {
                val fmt = DateTimeFormatter.ofPattern("MMM", Locale("de"))
                val byMonth = entries.groupBy { YearMonth.from(it.date) }
                val fromMonth = YearMonth.from(from)
                val toMonth = YearMonth.from(to)
                generateSequence(fromMonth) { m -> if (m < toMonth) m.plusMonths(1) else null }
                    .map { month -> ChartPoint(month.format(fmt), valueOf(byMonth[month].orEmpty())) }
                    .toList()
            }
        }
    }

    /** Clean share of a set of entries in percent (0–100): healthy kcal / total kcal; 0 if empty. */
    private fun cleanPercent(entries: List<FoodEntry>): Double {
        val total = entries.sumOf { it.kcal }
        if (total <= 0.0) return 0.0
        val clean = entries.filter { it.tag.isClean }.sumOf { it.kcal }
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
