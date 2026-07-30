package dev.antonlammers.trainist.ui.stats

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/** A single point on a measurement chart: an actual calendar date and a cm value. */
data class MeasurementSample(val date: LocalDate, val cm: Double)

/** Everything one measurement type's chart needs, mirroring [WeightChartData]'s shape. */
data class MeasurementChartData(
    val samples: List<MeasurementSample> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val minCm: Double = 0.0,
    val maxCm: Double = 0.0,
) {
    val hasData: Boolean get() = samples.isNotEmpty()

    /** Most recent measurement in the visible range. */
    val current: Double? get() = samples.lastOrNull()?.cm

    /** Change across the visible range (latest − earliest), or null with fewer than two samples. */
    val delta: Double? get() = if (samples.size >= 2) samples.last().cm - samples.first().cm else null
}

/**
 * Pure, Android-free measurement-series math, the [MeasurementType] counterpart to [WeightSeries].
 */
internal object MeasurementSeries {

    /**
     * [type]'s entries for WEEK/MONTH; for YEAR a single averaged sample per calendar month, placed
     * mid-month so it sits time-proportionally on the axis. Result is date-ascending.
     */
    fun samples(range: TimeRange, entries: List<BodyMeasurementEntry>, type: MeasurementType): List<MeasurementSample> {
        val forType = entries.filter { it.type == type }
        return when (range) {
            TimeRange.YEAR -> forType
                .groupBy { YearMonth.from(it.date) }
                .toSortedMap()
                .map { (month, group) -> MeasurementSample(month.atDay(15), group.map { it.valueCm }.average()) }
            else -> forType.sortedBy { it.date }.map { MeasurementSample(it.date, it.valueCm) }
        }
    }

    /**
     * Padded y-axis bounds covering all sample values. Padding is at least 0.5 cm; bounds round
     * outward to the nearest 0.5 cm so gridline labels read cleanly. Returns 0..0 when empty.
     */
    fun bounds(samples: List<MeasurementSample>): Pair<Double, Double> {
        if (samples.isEmpty()) return 0.0 to 0.0
        val values = samples.map { it.cm }
        val lo = values.min()
        val hi = values.max()
        val pad = maxOf(0.5, (hi - lo) * 0.15)
        return floorToHalf(lo - pad) to ceilToHalf(hi + pad)
    }

    private fun floorToHalf(v: Double) = floor(v * 2) / 2
    private fun ceilToHalf(v: Double) = ceil(v * 2) / 2
}
