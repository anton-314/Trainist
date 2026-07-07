package dev.antonlammers.macrotrac.ui.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.ui.data.DataViewModel
import dev.antonlammers.macrotrac.ui.theme.CalorieColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor
import dev.antonlammers.macrotrac.ui.theme.TagHealthyColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel(),
    dataViewModel: DataViewModel = hiltViewModel(),
) {
    val state by statsViewModel.uiState.collectAsStateWithLifecycle()
    val dataState by dataViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            snackbar.showSnackbar(it)
            dataViewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { dataViewModel.import(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistik") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                        label = { Text(range.label) },
                    )
                }
            }

            // Calorie chart
            Card(modifier = Modifier.fillMaxWidth()) {
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
                        CalorieBarChart(
                            points = state.caloriePoints,
                            goalKcal = state.goalKcal,
                            barColor = CalorieColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            goalColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Clean-eating chart
            Card(modifier = Modifier.fillMaxWidth()) {
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
            Card(modifier = Modifier.fillMaxWidth()) {
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

            // CSV section
            HorizontalDivider()
            Text("Daten", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (dataState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    dataViewModel.export { uri ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Exportieren via"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !dataState.isLoading,
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Vollständiges Backup exportieren")
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !dataState.isLoading,
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Backup importieren")
            }
        }
    }
}

@Composable
private fun CalorieBarChart(
    points: List<ChartPoint>,
    goalKcal: Double,
    barColor: Color,
    trackColor: Color,
    goalColor: Color,
    labelColor: Color,
) {
    val maxValue = maxOf(points.maxOf { it.value }, goalKcal, 1.0).toFloat()
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
                    drawRect(barColor, Offset(x + pad, size.height - fillHeight), Size(barWidth - pad * 2, fillHeight))
                }
            }

            if (goalKcal > 0f) {
                val goalY = size.height - (goalKcal.toFloat() / maxValue * size.height).coerceIn(0f, size.height)
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
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
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
