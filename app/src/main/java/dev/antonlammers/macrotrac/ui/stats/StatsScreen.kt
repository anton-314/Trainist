package dev.antonlammers.macrotrac.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.util.Locale
import kotlin.math.abs
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.ui.theme.CalorieColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor
import dev.antonlammers.macrotrac.ui.theme.TagHealthyColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel(),
) {
    val state by statsViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistik") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Time range selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.timeRange == range,
                        onClick = { statsViewModel.setTimeRange(range) },
                        label = {
                            Text(
                                range.label.uppercase(Locale("de")),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            // Calorie chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kalorien", style = MaterialTheme.typography.titleSmall)
                    if (state.caloriePoints.isEmpty() || state.caloriePoints.all { it.value == 0.0 }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Noch keine Einträge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        BarChart(
                            points = state.caloriePoints,
                            goalValue = state.goalKcal,
                            barColor = CalorieColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            goalColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Clean-eating chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Clean-Ernährung", style = MaterialTheme.typography.titleSmall)
                        state.overallCleanPercent?.let { pct ->
                            Text(
                                "Ø $pct %",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.overallCleanPercent == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Noch keine getaggten Einträge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        CleanBarChart(
                            points = state.cleanPoints,
                            barColor = TagHealthyColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Weight chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gewicht", style = MaterialTheme.typography.titleSmall)
                    val weight = state.weight
                    if (!weight.hasData) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Noch keine Gewichtsdaten",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        WeightSummary(weight)
                        WeightChart(
                            data = weight,
                            range = state.timeRange,
                            rawColor = ProteinColor,
                            trendColor = MaterialTheme.colorScheme.tertiary,
                            targetColor = MaterialTheme.colorScheme.primary,
                            gridColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Training frequency
            ChartCard("Trainingsfrequenz") {
                if (state.frequencyPoints.isEmpty() || state.frequencyPoints.all { it.value == 0.0 }) {
                    ChartEmptyHint("Noch keine Einheiten")
                } else {
                    BarChart(
                        points = state.frequencyPoints,
                        goalValue = 0.0,
                        barColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        goalColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Strength progression (per exercise)
            ChartCard("Kraftverlauf") {
                if (state.strengthExercises.isEmpty()) {
                    ChartEmptyHint("Noch keine Trainingsdaten")
                } else {
                    ExerciseSelector(
                        exercises = state.strengthExercises,
                        selectedId = state.selectedExerciseId,
                        onSelect = statsViewModel::setSelectedExercise,
                    )
                    if (!state.strength.hasData) {
                        ChartEmptyHint("Für diese Übung keine Daten im Zeitraum")
                    } else {
                        StrengthChart(
                            data = state.strength,
                            range = state.timeRange,
                            lineColor = MaterialTheme.colorScheme.primary,
                            gridColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Extra bottom breathing room so the last card clears the navigation bar.
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Dynamic-scale bar chart with rounded tops (the Canvas clips the bottom corners). An optional dashed
 * goal line is drawn when [goalValue] > 0 — shared by the calorie chart (goal = kcal target) and the
 * training-frequency chart (no goal line).
 */
@Composable
private fun BarChart(
    points: List<ChartPoint>,
    goalValue: Double,
    barColor: Color,
    trackColor: Color,
    goalColor: Color,
    labelColor: Color,
) {
    val maxValue = maxOf(points.maxOf { it.value }, goalValue, 1.0).toFloat()
    val labelStep = when {
        points.size <= 8 -> 1
        points.size <= 16 -> 2
        else -> (points.size / 6).coerceAtLeast(1)
    }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val barWidth = size.width / points.size
            val pad = (barWidth * 0.12f).coerceAtLeast(1.5.dp.toPx())

            points.forEachIndexed { i, point ->
                val fillFraction = (point.value.toFloat() / maxValue).coerceIn(0f, 1f)
                val fillHeight = fillFraction * size.height
                val x = i * barWidth

                drawRect(trackColor, Offset(x + pad, 0f), Size(barWidth - pad * 2, size.height))
                if (fillHeight > 0f) {
                    val radius = 4.dp.toPx().coerceAtMost(minOf((barWidth - pad * 2) / 2f, fillHeight))
                    // Extend past the baseline so only the top corners round; the Canvas clips the rest.
                    drawRoundRect(
                        barColor,
                        topLeft = Offset(x + pad, size.height - fillHeight),
                        size = Size(barWidth - pad * 2, fillHeight + radius),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }

            if (goalValue > 0f) {
                val goalY = size.height - (goalValue.toFloat() / maxValue * size.height).coerceIn(0f, size.height)
                drawLine(
                    goalColor,
                    Offset(0f, goalY),
                    Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, point ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelStep == 0) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Fixed 0–100 % bar chart for the clean-eating share, with a dashed target line. */
@Composable
private fun CleanBarChart(
    points: List<ChartPoint>,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
) {
    val labelStep = when {
        points.size <= 8 -> 1
        points.size <= 16 -> 2
        else -> (points.size / 6).coerceAtLeast(1)
    }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val barWidth = size.width / points.size
            val pad = (barWidth * 0.12f).coerceAtLeast(1.5.dp.toPx())

            points.forEachIndexed { i, point ->
                val fillFraction = (point.value.toFloat() / 100f).coerceIn(0f, 1f)
                val fillHeight = fillFraction * size.height
                val x = i * barWidth

                drawRect(trackColor, Offset(x + pad, 0f), Size(barWidth - pad * 2, size.height))
                if (fillHeight > 0f) {
                    drawRect(barColor, Offset(x + pad, size.height - fillHeight), Size(barWidth - pad * 2, fillHeight))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, point ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelStep == 0) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightSummary(data: WeightChartData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        data.current?.let { SummaryStat("Aktuell", "${formatKg(it)} kg") }
        data.delta?.let {
            val sign = if (it >= 0) "+" else "-"
            SummaryStat("Veränderung", "$sign${formatKg(abs(it))} kg")
        }
        data.targetKg?.let { SummaryStat("Ziel", "${formatKg(it)} kg") }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(Locale("de")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Time-proportional weight chart: x positions come from each sample's real date within the
 * visible range, y from a padded kg scale with gridline labels. Draws the raw weigh-ins, an
 * optional moving-average trend overlay, and a dashed target line.
 */
@Composable
private fun WeightChart(
    data: WeightChartData,
    range: TimeRange,
    rawColor: Color,
    trendColor: Color,
    targetColor: Color,
    gridColor: Color,
    labelColor: Color,
) {
    val tickDates = remember(data.rangeStart, data.rangeEnd, range) {
        weightTickDates(data.rangeStart, data.rangeEnd, range)
    }
    val tickFmt = remember(range) { weightTickFormatter(range) }

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val leftGutter = 36.dp.toPx()
        val bottomGutter = 18.dp.toPx()
        val plotLeft = leftGutter
        val plotTop = 6.dp.toPx()
        val plotWidth = size.width - leftGutter
        val plotHeight = size.height - bottomGutter - plotTop

        val startEpoch = data.rangeStart.toEpochDay()
        val daySpan = (data.rangeEnd.toEpochDay() - startEpoch).coerceAtLeast(1L)
        val kgSpan = (data.maxKg - data.minKg).takeIf { it > 0.0 } ?: 1.0

        fun xForDate(d: LocalDate): Float =
            plotLeft + ((d.toEpochDay() - startEpoch).toFloat() / daySpan) * plotWidth
        fun yForKg(kg: Double): Float =
            plotTop + (1f - ((kg - data.minKg) / kgSpan).toFloat()) * plotHeight

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }

        // Horizontal gridlines + kg labels at min / mid / max.
        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        listOf(data.minKg, (data.minKg + data.maxKg) / 2.0, data.maxKg).forEach { kg ->
            val y = yForKg(kg)
            drawLine(gridColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                formatKg(kg), plotLeft - 4.dp.toPx(), y + 3.5.dp.toPx(), labelPaint,
            )
        }

        // Dashed target line — only when it sits within the visible kg window.
        data.targetKg?.let { target ->
            if (target in data.minKg..data.maxKg) {
                val y = yForKg(target)
                drawLine(
                    targetColor,
                    Offset(plotLeft, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        }

        // X-axis date ticks.
        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        tickDates.forEach { d ->
            drawContext.canvas.nativeCanvas.drawText(
                d.format(tickFmt), xForDate(d).coerceIn(plotLeft, size.width), size.height, labelPaint,
            )
        }

        // Raw weigh-ins: faint line when a trend overlay is shown, full strength otherwise.
        val rawAlpha = if (data.trend.isNotEmpty()) 0.35f else 1f
        if (data.samples.size >= 2) {
            val path = Path().apply {
                moveTo(xForDate(data.samples.first().date), yForKg(data.samples.first().kg))
                data.samples.drop(1).forEach { lineTo(xForDate(it.date), yForKg(it.kg)) }
            }
            drawPath(
                path, rawColor.copy(alpha = rawAlpha),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        data.samples.forEach {
            drawCircle(rawColor, 3.5.dp.toPx(), Offset(xForDate(it.date), yForKg(it.kg)))
        }

        // Moving-average trend overlay.
        if (data.trend.size >= 2) {
            val path = Path().apply {
                moveTo(xForDate(data.trend.first().date), yForKg(data.trend.first().kg))
                data.trend.drop(1).forEach { lineTo(xForDate(it.date), yForKg(it.kg)) }
            }
            drawPath(
                path, trendColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Flat hairline chart card matching the existing stats cards; hosts a titled chart or empty hint. */
@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun ChartEmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Horizontally-scrollable pill chips picking the exercise the strength chart shows. */
@Composable
private fun ExerciseSelector(
    exercises: List<ExerciseOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        exercises.forEach { option ->
            FilterChip(
                selected = option.stableId == selectedId,
                onClick = { onSelect(option.stableId) },
                label = { Text(option.name, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/**
 * Time-proportional strength (estimated-1RM) line chart, mirroring the weight chart: x from each
 * sample's real date within the range, y from a padded kg scale with min/mid/max gridline labels.
 */
@Composable
private fun StrengthChart(
    data: StrengthChartData,
    range: TimeRange,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
) {
    val tickDates = remember(data.rangeStart, data.rangeEnd, range) {
        weightTickDates(data.rangeStart, data.rangeEnd, range)
    }
    val tickFmt = remember(range) { weightTickFormatter(range) }

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val leftGutter = 36.dp.toPx()
        val bottomGutter = 18.dp.toPx()
        val plotLeft = leftGutter
        val plotTop = 6.dp.toPx()
        val plotWidth = size.width - leftGutter
        val plotHeight = size.height - bottomGutter - plotTop

        val startEpoch = data.rangeStart.toEpochDay()
        val daySpan = (data.rangeEnd.toEpochDay() - startEpoch).coerceAtLeast(1L)
        val kgSpan = (data.maxKg - data.minKg).takeIf { it > 0.0 } ?: 1.0

        fun xForDate(d: LocalDate): Float =
            plotLeft + ((d.toEpochDay() - startEpoch).toFloat() / daySpan) * plotWidth
        fun yForKg(kg: Double): Float =
            plotTop + (1f - ((kg - data.minKg) / kgSpan).toFloat()) * plotHeight

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }

        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        listOf(data.minKg, (data.minKg + data.maxKg) / 2.0, data.maxKg).forEach { kg ->
            val y = yForKg(kg)
            drawLine(gridColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                formatKg(kg), plotLeft - 4.dp.toPx(), y + 3.5.dp.toPx(), labelPaint,
            )
        }

        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        tickDates.forEach { d ->
            drawContext.canvas.nativeCanvas.drawText(
                d.format(tickFmt), xForDate(d).coerceIn(plotLeft, size.width), size.height, labelPaint,
            )
        }

        if (data.samples.size >= 2) {
            val path = Path().apply {
                moveTo(xForDate(data.samples.first().date), yForKg(data.samples.first().estimatedOneRepMaxKg))
                data.samples.drop(1).forEach { lineTo(xForDate(it.date), yForKg(it.estimatedOneRepMaxKg)) }
            }
            drawPath(
                path, lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        data.samples.forEach {
            drawCircle(lineColor, 3.5.dp.toPx(), Offset(xForDate(it.date), yForKg(it.estimatedOneRepMaxKg)))
        }
    }
}

/** One decimal at most; whole numbers without a decimal point (72 / 72.5). */
private fun formatKg(kg: Double): String {
    val rounded = Math.round(kg * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun weightTickFormatter(range: TimeRange): DateTimeFormatter = when (range) {
    TimeRange.WEEK -> DateTimeFormatter.ofPattern("EE", Locale("de"))
    TimeRange.MONTH -> DateTimeFormatter.ofPattern("d.M.", Locale("de"))
    TimeRange.YEAR -> DateTimeFormatter.ofPattern("MMM", Locale("de"))
}

/** Evenly spaced tick dates across the range; count tuned per range to avoid label overlap. */
private fun weightTickDates(start: LocalDate, end: LocalDate, range: TimeRange): List<LocalDate> {
    val count = when (range) {
        TimeRange.WEEK -> 7
        TimeRange.MONTH -> 5
        TimeRange.YEAR -> 6
    }
    val span = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(1L)
    return (0 until count)
        .map { i -> LocalDate.ofEpochDay(start.toEpochDay() + span * i / (count - 1)) }
        .distinct()
}
