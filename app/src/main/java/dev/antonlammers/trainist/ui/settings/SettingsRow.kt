package dev.antonlammers.trainist.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The settings hub's building blocks.
 *
 * The hub is a short list of grouped rows rather than a long scroll of inline forms. What stays
 * inline is only what fits a single row (a switch, or a value); everything else opens a
 * [SettingsSheet] over the hub. Only a real editing form — the goal editor — is a pushed screen.
 */

/** Mono-uppercase caption above a group of related rows. */
@Composable
fun ColumnScope.SettingsGroupLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

/** Bordered card holding one or more [SettingsRow]s, separated by [SettingsRowDivider]. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Hairline between two rows of the same [SettingsGroup], inset to clear the leading icon. */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One settings row: leading icon, title, optional supporting line, and a trailing slot.
 *
 * @param value shown right-aligned before the trailing slot — lets a row report its current state
 * (the picked language, the calorie goal) without being opened.
 * @param onClick makes the whole row tappable; pass null for rows whose only control is [trailing]
 * (a switch handles its own input).
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        value?.let {
            Spacer(Modifier.width(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/**
 * One radio choice inside a [SettingsSheet] picker — the shared shape behind every "pick exactly
 * one" settings sheet (language, appearance), so they can't drift apart row by row. The whole row
 * is the tap target, not just the button.
 */
@Composable
fun SettingsOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = SHEET_CONTENT_PADDING, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Bottom breathing room so a screen's last card clears the navigation bar. */
@Composable
fun ColumnScope.SettingsBottomSpacer() {
    Spacer(Modifier.height(24.dp))
}
