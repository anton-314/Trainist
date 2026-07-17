package dev.antonlammers.trainist.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.util.currentAppLocale

/**
 * Shared goal-editor field building blocks, reused by the Settings goals editor and the first-run
 * onboarding guide so both surfaces stay visually identical. Kept here in the `goals` package (next
 * to [GoalsViewModel]) since the goal form is now hosted in more than one place.
 */

/** Mono-uppercase static caption shown above an input field. */
@Composable
internal fun FieldLabel(label: String) {
    Text(
        label.uppercase(currentAppLocale()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A goal input: a mono-uppercase caption above a serif-valued [NumericTextField], with an optional
 * 8×20dp colored accent tick (the macro's data color) in the leading slot.
 */
@Composable
internal fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    decimal: Boolean = false,
    suffix: String? = null,
    supportingText: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        NumericTextField(
            value = value,
            onValueChange = onValueChange,
            label = null,
            decimal = decimal,
            suffix = suffix,
            supportingText = supportingText,
            textStyle = MaterialTheme.typography.titleMedium,
            leadingIcon = accentColor?.let { color -> { AccentTick(color) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Small vertical accent bar carrying a macro's data color. */
@Composable
internal fun AccentTick(color: Color) {
    Box(
        Modifier
            .size(width = 8.dp, height = 20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/** Whole numbers without a decimal point, fractional weights as-is (e.g. 72 / 72.5). */
internal fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
