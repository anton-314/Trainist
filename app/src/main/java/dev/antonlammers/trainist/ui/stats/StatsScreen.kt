package dev.antonlammers.trainist.ui.stats

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.ui.components.DragReorderColumn
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.ui.theme.TagHealthyColor
import dev.antonlammers.trainist.ui.util.currentAppLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel(),
) {
    val state by statsViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.stats_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Time range selector — pinned above the scrolling content so it's always reachable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.timeRange == range,
                        onClick = { statsViewModel.setTimeRange(range) },
                        label = {
                            Text(
                                range.label().uppercase(currentAppLocale()),
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Chart cards — user-orderable via drag handle (spec addendum: consistent with the
                // workout module's drag-to-reorder pattern).
                DragReorderColumn(
                    items = state.cardOrder,
                    key = { it },
                    onMove = statsViewModel::moveCard,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { _, card, rowModifier, dragHandleModifier, isDragging ->
                    when (card) {
                        StatCardType.CALORIES -> ChartCard(stringResource(R.string.stats_card_calories), rowModifier, dragHandleModifier, isDragging) {
                            if (state.caloriePoints.isEmpty() || state.caloriePoints.all { it.value == 0.0 }) {
                                ChartEmptyHint(stringResource(R.string.stats_empty_calories))
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
                        StatCardType.CLEAN_EATING -> ChartCard(
                            title = stringResource(R.string.stats_card_clean_eating),
                            rowModifier = rowModifier,
                            dragHandleModifier = dragHandleModifier,
                            isDragging = isDragging,
                            trailing = {
                                state.overallCleanPercent?.let { pct ->
                                    Text(
                                        stringResource(R.string.stats_clean_average, pct),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            if (state.overallCleanPercent == null) {
                                ChartEmptyHint(stringResource(R.string.stats_empty_clean_eating))
                            } else {
                                BarChart(
                                    points = state.cleanPoints,
                                    goalValue = 0.0,
                                    fixedMaxValue = 100.0,
                                    barColor = TagHealthyColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    goalColor = MaterialTheme.colorScheme.primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StatCardType.WEIGHT -> ChartCard(stringResource(R.string.stats_card_weight), rowModifier, dragHandleModifier, isDragging) {
                            val weight = state.weight
                            if (!weight.hasData) {
                                ChartEmptyHint(stringResource(R.string.stats_empty_weight))
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
                        StatCardType.TRAINING_FREQUENCY -> ChartCard(stringResource(R.string.stats_card_training_frequency), rowModifier, dragHandleModifier, isDragging) {
                            if (state.frequencyPoints.isEmpty() || state.frequencyPoints.all { it.value == 0.0 }) {
                                ChartEmptyHint(stringResource(R.string.stats_empty_frequency))
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
                        StatCardType.STRENGTH -> ChartCard(stringResource(R.string.stats_card_strength), rowModifier, dragHandleModifier, isDragging) {
                            if (state.strengthExercises.isEmpty()) {
                                ChartEmptyHint(stringResource(R.string.stats_empty_strength))
                            } else {
                                ExerciseSelector(
                                    exercises = state.strengthExercises,
                                    selectedId = state.selectedExerciseId,
                                    onSelect = statsViewModel::setSelectedExercise,
                                )
                                if (!state.strength.hasData) {
                                    ChartEmptyHint(stringResource(R.string.stats_empty_strength_exercise))
                                } else {
                                    val data = state.strength
                                    val range = state.timeRange
                                    val tickDates = remember(data.rangeStart, data.rangeEnd, range) {
                                        weightTickDates(data.rangeStart, data.rangeEnd, range)
                                    }
                                    val tickFmt = remember(range) { weightTickFormatter(range) }
                                    StrengthChart(
                                        data = data,
                                        tickDates = tickDates,
                                        tickFormatter = tickFmt,
                                        lineColor = MaterialTheme.colorScheme.primary,
                                        gridColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Extra bottom breathing room so the last card clears the navigation bar.
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Bar chart with rounded tops, shared by every bar-based chart on the screen (calorie, clean-eating,
 * training-frequency) so their corner styling never drifts apart again. An optional dashed goal line
 * is drawn when [goalValue] > 0. The scale is dynamic (max of the data and [goalValue]) unless
 * [fixedMaxValue] is given — the clean-eating chart pins it to 100 so its axis always reads as a
 * percentage share, not a dynamic range.
 *
 * The rounded top is drawn by extending the bar's rect past the baseline and letting [clipRect] cut
 * the overshoot back off at the Canvas edge — without that explicit clip the overshoot bleeds past
 * the chart into the labels below (Compose's `Canvas` does not clip overflow on its own).
 */
@Composable
private fun BarChart(
    points: List<ChartPoint>,
    goalValue: Double,
    barColor: Color,
    trackColor: Color,
    goalColor: Color,
    labelColor: Color,
    fixedMaxValue: Double? = null,
) {
    val maxValue = (fixedMaxValue ?: maxOf(points.maxOf { it.value }, goalValue, 1.0)).toFloat()
    val labelStep = when {
        points.size <= 8 -> 1
        points.size <= 16 -> 2
        else -> (points.size / 6).coerceAtLeast(1)
    }

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            clipRect {
                val barWidth = size.width / points.size
                val pad = (barWidth * 0.12f).coerceAtLeast(1.5.dp.toPx())

                points.forEachIndexed { i, point ->
                    val fillFraction = (point.value.toFloat() / maxValue).coerceIn(0f, 1f)
                    val fillHeight = fillFraction * size.height
                    val x = i * barWidth

                    drawRect(trackColor, Offset(x + pad, 0f), Size(barWidth - pad * 2, size.height))
                    if (fillHeight > 0f) {
                        val radius = 4.dp.toPx().coerceAtMost(minOf((barWidth - pad * 2) / 2f, fillHeight))
                        // Extend past the baseline so only the top corners round; clipRect above cuts the rest.
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
        data.current?.let { SummaryStat(stringResource(R.string.stats_weight_current), "${formatKg(it)} kg") }
        data.delta?.let {
            val sign = if (it >= 0) "+" else "-"
            SummaryStat(stringResource(R.string.stats_weight_change), "$sign${formatKg(abs(it))} kg")
        }
        data.targetKg?.let { SummaryStat(stringResource(R.string.stats_weight_target), "${formatKg(it)} kg") }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(currentAppLocale()),
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

/** One draggable chart card. [rowModifier]/[dragHandleModifier] come from the enclosing [DragReorderColumn]. */
@Composable
private fun ChartCard(
    title: String,
    rowModifier: Modifier,
    dragHandleModifier: Modifier,
    isDragging: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = rowModifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(R.string.workout_session_drag_handle_content_description),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = dragHandleModifier.size(20.dp),
                    )
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                trailing?.invoke()
            }
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

@Composable
private fun TimeRange.label(): String = stringResource(
    when (this) {
        TimeRange.WEEK -> R.string.stats_time_range_week
        TimeRange.MONTH -> R.string.stats_time_range_month
        TimeRange.YEAR -> R.string.stats_time_range_year
    },
)

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
    return evenlySpacedDates(start, end, count)
}
