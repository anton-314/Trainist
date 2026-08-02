package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.SupersetPlacement

/**
 * Shared superset vocabulary — the one place a superset group turns into something visible, used by
 * both the live session and the history day detail so the two can't drift apart.
 *
 * The domain deliberately produces no display text (see `Supersets`), only a 0-based group ordinal;
 * [supersetLetter] is where that becomes the A / B / C a lifter actually reads.
 */

/** Display letter for a 0-based superset group ordinal: 0 → "A", 1 → "B", … */
internal fun supersetLetter(ordinal: Int): String =
    ('A' + (ordinal.coerceAtLeast(0) % 26)).toString()

/**
 * The "SUPERSATZ A · 2/3" marker sitting above a grouped exercise's name: which group it belongs to
 * and where in the group it falls. Accent-tinted, because within Ink & Paper colour marks data — and
 * the grouping *is* the datum here.
 */
@Composable
internal fun SupersetBadge(
    placement: SupersetPlacement,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.workout_superset_badge,
            supersetLetter(placement.ordinal),
            placement.member,
            placement.size,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = BADGE_TINT_ALPHA))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Faint enough that the badge reads as a tint on the card, not as a filled accent chip. */
private const val BADGE_TINT_ALPHA = 0.12f
