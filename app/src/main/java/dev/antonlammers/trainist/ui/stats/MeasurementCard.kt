package dev.antonlammers.trainist.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.MeasurementType
import kotlin.math.abs

/** Localized display name for a [MeasurementType] — kept in the UI layer like [dev.antonlammers.trainist.domain.model.FoodTag]'s. */
@Composable
internal fun MeasurementType.displayName(): String = stringResource(
    when (this) {
        MeasurementType.NECK -> R.string.measurement_type_neck
        MeasurementType.CHEST -> R.string.measurement_type_chest
        MeasurementType.WAIST -> R.string.measurement_type_waist
        MeasurementType.HIPS -> R.string.measurement_type_hips
        MeasurementType.BICEPS -> R.string.measurement_type_biceps
        MeasurementType.THIGH -> R.string.measurement_type_thigh
        MeasurementType.CALF -> R.string.measurement_type_calf
    },
)

/**
 * Body of the **Körpermaße** card: a type selector (fixed set of chips — unlike the strength chart's
 * exercise selector, every [MeasurementType] is always offered, not just the ones with data), a
 * current/change summary, the time-proportional chart, and a "+" button opening the entry sheet.
 */
@Composable
internal fun MeasurementCard(
    state: MeasurementCardState,
    range: TimeRange,
    onSelectType: (MeasurementType) -> Unit,
    onAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeasurementType.entries.forEach { type ->
                FilterChip(
                    selected = type == state.selectedType,
                    onClick = { onSelectType(type) },
                    label = { Text(type.displayName(), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        FilledIconButton(
            onClick = onAddClick,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.measurement_add_content_description),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    val chart = state.chart
    if (!chart.hasData) {
        ChartEmptyHint(stringResource(R.string.stats_empty_measurements_type))
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        chart.current?.let { SummaryStat(stringResource(R.string.stats_weight_current), "${formatCm(it)} cm") }
        chart.delta?.let {
            val sign = if (it >= 0) "+" else "-"
            SummaryStat(stringResource(R.string.stats_weight_change), "$sign${formatCm(abs(it))} cm")
        }
    }

    val tickDates = remember(chart.rangeStart, chart.rangeEnd, range) {
        weightTickDates(chart.rangeStart, chart.rangeEnd, range)
    }
    val tickFmt = remember(range) { weightTickFormatter(range) }
    TimeLineChart(
        points = chart.samples.map { TimeValue(it.date, it.cm) },
        rangeStart = chart.rangeStart,
        rangeEnd = chart.rangeEnd,
        minValue = chart.minCm,
        maxValue = chart.maxCm,
        tickDates = tickDates,
        tickFormatter = tickFmt,
        valueLabel = ::formatCm,
        lineColor = MaterialTheme.colorScheme.primary,
        gridColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One decimal at most; whole numbers without a decimal point (82 / 82.5). */
internal fun formatCm(cm: Double): String {
    val rounded = Math.round(cm * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
