package dev.antonlammers.trainist.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.ProgressionAdvice
import dev.antonlammers.trainist.domain.ProgressionTrend
import dev.antonlammers.trainist.ui.theme.TagHealthyColor
import dev.antonlammers.trainist.ui.theme.TagNeutralColor
import dev.antonlammers.trainist.ui.util.currentAppLocale
import dev.antonlammers.trainist.ui.util.localizedDateFormatter

/**
 * Body of the **progressive-overload** card: one headline number answering "am I getting stronger
 * overall?", the chain-linked strength index across all exercises, and the advisor's ranked tips.
 *
 * The index is drawn against a dashed 100 % baseline (its starting level), so the whole card can be
 * read at a glance: above the line is progress, on it is maintenance. The tips come from
 * [dev.antonlammers.trainist.domain.ProgressionAdvisor] as enum constants and are mapped to strings
 * here — the ViewModel never builds display text.
 */
@Composable
internal fun OverloadCard(state: OverloadCardState) {
    val chart = state.chart
    if (chart.points.isEmpty()) {
        ChartEmptyHint(stringResource(R.string.stats_overload_empty))
        return
    }

    val trendColor = state.trend.color()

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = state.changePercent?.let { formatSignedPercent(it) } ?: "–",
            style = MaterialTheme.typography.headlineMedium,
            color = trendColor,
        )
        Text(
            text = stringResource(state.trend.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = trendColor,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Text(
        text = stringResource(
            R.string.stats_overload_meta,
            chart.trackedExercises,
            state.sessions,
            state.trainingWeeks,
        ).uppercase(currentAppLocale()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (chart.hasData) {
        val tickDates = remember(chart.rangeStart, chart.rangeEnd) {
            evenlySpacedDates(chart.rangeStart, chart.rangeEnd, OVERLOAD_TICK_COUNT)
        }
        TimeLineChart(
            points = chart.points.map { TimeValue(it.date, it.index) },
            rangeStart = chart.rangeStart,
            rangeEnd = chart.rangeEnd,
            minValue = chart.minIndex,
            maxValue = chart.maxIndex,
            tickDates = tickDates,
            tickFormatter = localizedDateFormatter("d.M."),
            valueLabel = ::formatIndex,
            lineColor = trendColor,
            gridColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            referenceValue = BASE_INDEX,
            referenceColor = MaterialTheme.colorScheme.outline,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.advice.forEach { advice ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Plain bullet dot rather than an icon — the tips are prose, not actions.
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(trendColor),
                )
                Text(
                    text = stringResource(advice.textRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ticks on the fixed 12-week axis — enough to orient without the labels colliding. */
private const val OVERLOAD_TICK_COUNT = 5

/** Signed, one-decimal percentage ("+4,2 %"), rendered in the app's locale. */
private fun formatSignedPercent(value: Double): String =
    String.format(currentAppLocale(), "%+.1f %%", value)

/** Y-axis label of the index — whole percent, no decimals (the index reads as a percentage). */
private fun formatIndex(value: Double): String = "${Math.round(value)}%"

@Composable
private fun ProgressionTrend.color(): Color = when (this) {
    ProgressionTrend.PROGRESSING -> TagHealthyColor
    ProgressionTrend.MAINTAINING -> TagNeutralColor
    ProgressionTrend.DECLINING -> MaterialTheme.colorScheme.error
    ProgressionTrend.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ProgressionTrend.labelRes(): Int = when (this) {
    ProgressionTrend.INSUFFICIENT_DATA -> R.string.stats_overload_trend_insufficient_data
    ProgressionTrend.PROGRESSING -> R.string.stats_overload_trend_progressing
    ProgressionTrend.MAINTAINING -> R.string.stats_overload_trend_maintaining
    ProgressionTrend.DECLINING -> R.string.stats_overload_trend_declining
}

private fun ProgressionAdvice.textRes(): Int = when (this) {
    ProgressionAdvice.COLLECT_MORE_DATA -> R.string.stats_overload_advice_collect_more_data
    ProgressionAdvice.KEEP_GOING -> R.string.stats_overload_advice_keep_going
    ProgressionAdvice.RECENT_STALL -> R.string.stats_overload_advice_recent_stall
    ProgressionAdvice.EXPECTED_IN_DEFICIT -> R.string.stats_overload_advice_expected_in_deficit
    ProgressionAdvice.ENERGY_DEFICIT -> R.string.stats_overload_advice_energy_deficit
    ProgressionAdvice.PROTEIN_LOW -> R.string.stats_overload_advice_protein_low
    ProgressionAdvice.RECOVERY_FREQUENCY -> R.string.stats_overload_advice_recovery_frequency
    ProgressionAdvice.CONSISTENCY -> R.string.stats_overload_advice_consistency
    ProgressionAdvice.DELOAD -> R.string.stats_overload_advice_deload
    ProgressionAdvice.PROGRESSION_SCHEME -> R.string.stats_overload_advice_progression_scheme
}
