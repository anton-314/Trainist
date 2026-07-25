package dev.antonlammers.trainist.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Strength (estimated-1RM) line chart — shared by the Statistik tab's "Kraftverlauf" card and the
 * exercise-detail mini-chart. A thin kg-flavoured binding of the shared [TimeLineChart]: the caller
 * supplies the x-axis [tickDates] + [tickFormatter] so the same chart serves both a fixed
 * WEEK/MONTH/YEAR axis and an all-time detail axis. [modifier] sets the canvas size (height).
 */
@Composable
internal fun StrengthChart(
    data: StrengthChartData,
    tickDates: List<LocalDate>,
    tickFormatter: DateTimeFormatter,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
) {
    TimeLineChart(
        points = data.samples.map { TimeValue(it.date, it.estimatedOneRepMaxKg) },
        rangeStart = data.rangeStart,
        rangeEnd = data.rangeEnd,
        minValue = data.minKg,
        maxValue = data.maxKg,
        tickDates = tickDates,
        tickFormatter = tickFormatter,
        valueLabel = ::formatKg,
        lineColor = lineColor,
        gridColor = gridColor,
        labelColor = labelColor,
        modifier = modifier,
    )
}

/** One decimal at most; whole numbers without a decimal point (72 / 72.5). Shared by the charts. */
internal fun formatKg(kg: Double): String {
    val rounded = Math.round(kg * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** [count] evenly spaced dates across `[start, end]` (inclusive), de-duplicated. */
internal fun evenlySpacedDates(start: LocalDate, end: LocalDate, count: Int): List<LocalDate> {
    if (count <= 1) return listOf(start)
    val span = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(1L)
    return (0 until count)
        .map { i -> LocalDate.ofEpochDay(start.toEpochDay() + span * i / (count - 1)) }
        .distinct()
}
