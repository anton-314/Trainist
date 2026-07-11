package dev.antonlammers.macrotrac.ui.stats

import dev.antonlammers.macrotrac.domain.WorkoutMetrics
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** One point on the strength chart: an actual calendar date and an estimated-1RM value (kg). */
data class StrengthSample(val date: LocalDate, val estimatedOneRepMaxKg: Double)

/**
 * Everything the strength chart needs, derived purely from the sessions in range. Like
 * [WeightChartData] the x-axis is time-based ([rangeStart]..[rangeEnd]) so callers place each sample
 * by its real date.
 */
data class StrengthChartData(
    val samples: List<StrengthSample> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val minKg: Double = 0.0,
    val maxKg: Double = 0.0,
) {
    val hasData: Boolean get() = samples.isNotEmpty()
}

/** One selectable exercise for the strength chart (stable key + display name). */
data class ExerciseOption(val stableId: String, val name: String)

/**
 * Pure, Android-free training-series math (spec §3.7) so the stats logic stays unit-testable — the
 * training counterpart to [WeightSeries]. Buckets align with the existing calorie/weight charts
 * (per day for WEEK/MONTH, per month for YEAR) over the same [TimeRange] axis.
 */
internal object WorkoutSeries {

    /**
     * Training frequency: the number of sessions per time bucket over [from]..[to]. Empty buckets
     * yield 0. [sessionDates] are the dates of the completed sessions in range (one entry per session).
     */
    fun frequency(range: TimeRange, from: LocalDate, to: LocalDate, sessionDates: List<LocalDate>): List<ChartPoint> =
        when (range) {
            TimeRange.WEEK, TimeRange.MONTH -> {
                val fmt = if (range == TimeRange.WEEK) DAY_OF_WEEK_FMT else DAY_OF_MONTH_FMT
                val byDate = sessionDates.groupingBy { it }.eachCount()
                generateSequence(from) { d -> if (d < to) d.plusDays(1) else null }
                    .map { date -> ChartPoint(date.format(fmt), (byDate[date] ?: 0).toDouble()) }
                    .toList()
            }
            TimeRange.YEAR -> {
                val byMonth = sessionDates.groupingBy { YearMonth.from(it) }.eachCount()
                val fromMonth = YearMonth.from(from)
                val toMonth = YearMonth.from(to)
                generateSequence(fromMonth) { m -> if (m < toMonth) m.plusMonths(1) else null }
                    .map { month -> ChartPoint(month.format(MONTH_FMT), (byMonth[month] ?: 0).toDouble()) }
                    .toList()
            }
        }

    /**
     * Strength progression for [exerciseStableId]: the best estimated 1RM (Epley) of each session
     * that trained it, placed on the time axis. For WEEK/MONTH one sample per session-day (the day's
     * best if trained twice); for YEAR one sample per calendar month (the month's best, placed
     * mid-month). date-ascending. Warm-up-only occurrences yield no sample (the best 1RM is null).
     */
    fun strengthSamples(
        range: TimeRange,
        sessions: List<WorkoutSession>,
        exerciseStableId: String,
        typeOf: (String) -> ExerciseType,
        bodyWeightForDate: (LocalDate) -> Double?,
    ): List<StrengthSample> {
        val perDate = sessions.mapNotNull { session ->
            val exercise = session.exercises.firstOrNull { it.exerciseStableId == exerciseStableId }
                ?: return@mapNotNull null
            val best = WorkoutMetrics.bestEstimatedOneRepMaxKg(
                exercise.sets,
                typeOf(exerciseStableId),
                bodyWeightForDate(session.date),
            ) ?: return@mapNotNull null
            session.date to best
        }
        return when (range) {
            TimeRange.YEAR -> perDate
                .groupBy { YearMonth.from(it.first) }
                .toSortedMap()
                .map { (month, group) -> StrengthSample(month.atDay(15), group.maxOf { it.second }) }
            else -> perDate
                .groupBy { it.first }
                .toSortedMap()
                .map { (date, group) -> StrengthSample(date, group.maxOf { it.second }) }
        }
    }

    /**
     * Padded y-axis bounds covering all sample values. Padding is at least 0.5 kg; bounds round
     * outward to the nearest 0.5 kg so gridline labels read cleanly. Returns 0..0 when empty.
     */
    fun bounds(samples: List<StrengthSample>): Pair<Double, Double> {
        if (samples.isEmpty()) return 0.0 to 0.0
        val values = samples.map { it.estimatedOneRepMaxKg }
        val lo = values.min()
        val hi = values.max()
        val pad = maxOf(0.5, (hi - lo) * 0.15)
        return floorToHalf(lo - pad) to ceilToHalf(hi + pad)
    }

    private fun floorToHalf(v: Double) = floor(v * 2) / 2
    private fun ceilToHalf(v: Double) = ceil(v * 2) / 2

    private val DAY_OF_WEEK_FMT = DateTimeFormatter.ofPattern("EE", Locale("de"))
    private val DAY_OF_MONTH_FMT = DateTimeFormatter.ofPattern("d", Locale("de"))
    private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM", Locale("de"))
}
