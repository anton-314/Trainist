package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.ui.theme.TagNeutralColor

/**
 * UI-layer presentation for [SetType]. Colours reuse **existing** data/theme tokens (no new colours,
 * per spec §6): NORMAL stays neutral, WARMUP borrows the amber tag tone, DROP the accent, FAILURE
 * the error/red tone.
 */
@Composable
fun SetType.color(): Color = when (this) {
    SetType.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    SetType.WARMUP -> TagNeutralColor
    SetType.DROP -> MaterialTheme.colorScheme.primary
    SetType.FAILURE -> MaterialTheme.colorScheme.error
}

@Composable
fun SetType.displayName(): String = stringResource(
    when (this) {
        SetType.NORMAL -> R.string.workout_set_type_normal
        SetType.WARMUP -> R.string.workout_set_type_warmup
        SetType.DROP -> R.string.workout_set_type_drop
        SetType.FAILURE -> R.string.workout_set_type_failure
    },
)

/** Compact marker shown next to the set number for non-normal set types (e.g. "W", "D", "F"). */
fun SetType.shortLabel(): String = when (this) {
    SetType.NORMAL -> ""
    SetType.WARMUP -> "W"
    SetType.DROP -> "D"
    SetType.FAILURE -> "F"
}

/**
 * The leading set marker shared by the live session and the history editor: the set number tinted by
 * its [SetType] (plus a short W/D/F tag for non-normal types). Tapping opens a menu to change the
 * type. Discreet, colour-token-only (spec §6).
 */
@Composable
fun SetTypeBadge(
    setNumber: Int,
    type: SetType,
    onTypeChange: (SetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tint = type.color()
    androidx.compose.foundation.layout.Box(modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { menuOpen = true }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(setNumber.toString(), style = MaterialTheme.typography.labelMedium, color = tint)
            if (type != SetType.NORMAL) {
                Text(type.shortLabel(), style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SetType.selectable.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName(), color = option.color()) },
                    onClick = { menuOpen = false; onTypeChange(option) },
                )
            }
        }
    }
}
