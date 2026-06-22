package dev.antonlammers.macrotrac.ui.stats

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/** A single point on the weight chart: an actual calendar date and a kg value. */
data class WeightSample(val date: LocalDate, val kg: Double)

/**
 * Everything the weight chart needs, derived purely from the weigh-ins in range plus the
 * optional target. The x-axis is time-based ([rangeStart]..[rangeEnd]), so callers place
 * each sample by its real [WeightSample.date] — not by index.
 */
data class WeightChartData(
    val samples: List<WeightSample> = emptyList(),
    val trend: List<WeightSample> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val minKg: Double = 0.0,
    val maxKg: Double = 0.0,
    val targetKg: Double? = null,
) {
    val hasData: Boolean get() = samples.isNotEmpty()

    /** Most recent weigh-in in the visible range. */
    val current: Double? get() = samples.lastOrNull()?.kg

    /** Change across the visible range (latest − earliest), or null with fewer than two samples. */
    val delta: Double? get() = if (samples.size >= 2) samples.last().kg - samples.first().kg else null
}

/**
 * Pure, Android-free weight-series math so the stats logic stays unit-testable.
 */
internal object WeightSeries {

    /** Trailing moving-average window per range; 0 means "no trend line for this range". */
    fun trendWindowDays(range: TimeRange): Int = when (range) {
        TimeRange.WEEK -> 0      // too few days for a meaningful average
        TimeRange.MONTH -> 7
        TimeRange.YEAR -> 90
    }

    /**
     * Raw weigh-ins for WEEK/MONTH; for YEAR a single averaged sample per calendar month,
     * placed mid-month so it sits time-proportionally on the axis. Result is date-ascending.
     */
    fun samples(range: TimeRange, entries: List<WeightEntry>): List<WeightSample> = when (range) {
        TimeRange.YEAR -> entries
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .map { (month, group) -> WeightSample(month.atDay(15), group.map { it.weightKg }.average()) }
        else -> entries.sortedBy { it.date }.map { WeightSample(it.date, it.weightKg) }
    }

    /**
     * Trailing moving average: each sample becomes the mean of all samples within the preceding
     * [windowDays] (inclusive of itself). Empty when the window is 0 or fewer than two samples
     * exist — there is nothing meaningful to smooth.
     */
    fun movingAverage(samples: List<WeightSample>, windowDays: Int): List<WeightSample> {
        if (windowDays <= 0 || samples.size < 2) return emptyList()
        return samples.map { sample ->
            val windowStart = sample.date.minusDays(windowDays.toLong())
            val window = samples.filter { it.date > windowStart && it.date <= sample.date }
            WeightSample(sample.date, window.map { it.kg }.average())
        }
    }

    /**
     * Padded y-axis bounds covering all sample values and the target (if set). Padding is at
     * least 0.5 kg; bounds round outward to the nearest 0.5 kg so gridline labels read cleanly.
     * Returns 0..0 when there is nothing to plot.
     */
    fun bounds(samples: List<WeightSample>, target: Double?): Pair<Double, Double> {
        val values = samples.map { it.kg } + listOfNotNull(target)
        if (values.isEmpty()) return 0.0 to 0.0
        val lo = values.min()
        val hi = values.max()
        val pad = maxOf(0.5, (hi - lo) * 0.15)
        return floorToHalf(lo - pad) to ceilToHalf(hi + pad)
    }

    private fun floorToHalf(v: Double) = floor(v * 2) / 2
    private fun ceilToHalf(v: Double) = ceil(v * 2) / 2
}
