package dev.antonlammers.trainist.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One point of a time-proportional line chart: a real calendar date and its value. */
internal data class TimeValue(val date: LocalDate, val value: Double)

/**
 * The one time-proportional line chart on the Stats screen — x from each point's real date within
 * `[rangeStart, rangeEnd]`, y from a caller-supplied value scale with min/mid/max gridline labels.
 * [StrengthChart] (Kraftverlauf + exercise detail) and the progressive-overload chart both render
 * through it, so their styling can't drift apart.
 *
 * The caller supplies [tickDates] + [tickFormatter] (so the same chart serves a fixed WEEK/MONTH/YEAR
 * axis and an all-time detail axis) and [valueLabel] for the y-axis text. An optional
 * [referenceValue] draws a dashed horizontal baseline — the overload chart marks its 100 % start
 * line with it. [modifier] sets the canvas size.
 */
@Composable
internal fun TimeLineChart(
    points: List<TimeValue>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    minValue: Double,
    maxValue: Double,
    tickDates: List<LocalDate>,
    tickFormatter: DateTimeFormatter,
    valueLabel: (Double) -> String,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    referenceValue: Double? = null,
    referenceColor: Color = lineColor,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
) {
    Canvas(modifier = modifier) {
        val leftGutter = 36.dp.toPx()
        val bottomGutter = 18.dp.toPx()
        val plotLeft = leftGutter
        val plotTop = 6.dp.toPx()
        val plotWidth = size.width - leftGutter
        val plotHeight = size.height - bottomGutter - plotTop

        val startEpoch = rangeStart.toEpochDay()
        val daySpan = (rangeEnd.toEpochDay() - startEpoch).coerceAtLeast(1L)
        val valueSpan = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

        fun xForDate(d: LocalDate): Float =
            plotLeft + ((d.toEpochDay() - startEpoch).toFloat() / daySpan) * plotWidth
        fun yForValue(v: Double): Float =
            plotTop + (1f - ((v - minValue) / valueSpan).toFloat()) * plotHeight

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }

        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        listOf(minValue, (minValue + maxValue) / 2.0, maxValue).forEach { value ->
            val y = yForValue(value)
            drawLine(gridColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                valueLabel(value), plotLeft - 4.dp.toPx(), y + 3.5.dp.toPx(), labelPaint,
            )
        }

        // Dashed reference baseline — only when it sits inside the visible value window.
        referenceValue?.let { reference ->
            if (reference in minValue..maxValue) {
                val y = yForValue(reference)
                drawLine(
                    referenceColor,
                    Offset(plotLeft, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        }

        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        tickDates.forEach { d ->
            drawContext.canvas.nativeCanvas.drawText(
                d.format(tickFormatter), xForDate(d).coerceIn(plotLeft, size.width), size.height, labelPaint,
            )
        }

        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(xForDate(points.first().date), yForValue(points.first().value))
                points.drop(1).forEach { lineTo(xForDate(it.date), yForValue(it.value)) }
            }
            drawPath(
                path, lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        points.forEach {
            drawCircle(lineColor, 3.5.dp.toPx(), Offset(xForDate(it.date), yForValue(it.value)))
        }
    }
}
