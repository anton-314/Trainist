package dev.antonlammers.trainist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor

/**
 * A food's or meal's macro split as one rounded bar: protein, carbs and fat segments in their macro
 * colours, each as wide as its share of the grams, with 2 dp gaps. A macro at 0 g takes no segment
 * (and no gap); with no macros at all the bar is an empty track.
 */
@Composable
fun MacroBar(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val segments = listOf(proteinG to ProteinColor, carbsG to CarbsColor, fatG to FatColor)
        .filter { (grams, _) -> grams > 0 }
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .then(if (segments.isEmpty()) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (grams, color) ->
            Box(Modifier.weight(grams.toFloat()).fillMaxHeight().background(color))
        }
    }
}
