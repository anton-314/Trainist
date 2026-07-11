package dev.antonlammers.macrotrac.ui.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.ui.theme.TagNeutralColor

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

fun SetType.displayName(): String = when (this) {
    SetType.NORMAL -> "Normal"
    SetType.WARMUP -> "Aufwärmen"
    SetType.DROP -> "Drop-Satz"
    SetType.FAILURE -> "Failure"
}

/** Compact marker shown next to the set number for non-normal set types (e.g. "W", "D", "F"). */
fun SetType.shortLabel(): String = when (this) {
    SetType.NORMAL -> ""
    SetType.WARMUP -> "W"
    SetType.DROP -> "D"
    SetType.FAILURE -> "F"
}
