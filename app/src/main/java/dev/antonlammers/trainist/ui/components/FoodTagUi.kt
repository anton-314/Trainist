package dev.antonlammers.trainist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.ui.theme.TagHealthyColor
import dev.antonlammers.trainist.ui.theme.TagNeutralColor
import dev.antonlammers.trainist.ui.theme.TagUnhealthyColor

/** The colour representing a tag. [FoodTag.NONE] uses the neutral grey of the current theme. */
@Composable
fun FoodTag.color(): Color = when (this) {
    FoodTag.HEALTHY -> TagHealthyColor
    FoodTag.NEUTRAL -> TagNeutralColor
    FoodTag.UNHEALTHY -> TagUnhealthyColor
    FoodTag.NONE -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun FoodTag.displayName(): String = stringResource(
    when (this) {
        FoodTag.HEALTHY -> R.string.food_tag_healthy
        FoodTag.NEUTRAL -> R.string.food_tag_neutral
        FoodTag.UNHEALTHY -> R.string.food_tag_unhealthy
        FoodTag.NONE -> R.string.food_tag_none
    },
)

/** Small colour dot preceding a food's name in a list. Renders nothing for the untagged default. */
@Composable
fun TagDot(tag: FoodTag, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    if (tag == FoodTag.NONE) return
    Box(modifier.size(size).clip(CircleShape).background(tag.color()))
}

/**
 * Chip row for picking a food's clean-eating tag. Tapping the already-selected chip clears the tag
 * back to [FoodTag.NONE] (grey / untagged), so "no tag" is always reachable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(
    selected: FoodTag,
    onSelected: (FoodTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.food_tag_selector_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodTag.selectable.forEach { tag ->
                val isSelected = tag == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(if (isSelected) FoodTag.NONE else tag) },
                    label = { Text(tag.displayName()) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(tag.color()))
                    },
                )
            }
        }
    }
}
