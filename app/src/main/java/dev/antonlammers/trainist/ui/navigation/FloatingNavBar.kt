package dev.antonlammers.trainist.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antonlammers.trainist.ui.theme.LocalInkTheme
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import dev.antonlammers.trainist.ui.tutorial.tutorialAnchor
import kotlin.math.roundToInt

/** One tab of the floating pill. */
internal data class NavTab(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Set for the tabs the guided tour points at; null for the ones it reaches by content instead. */
    val tutorialTarget: TutorialTarget? = null,
)

/** Clearance a tab screen leaves below its last item so it can scroll out from under the pill. */
val FloatingNavClearance = 110.dp

/** Lifts a tab screen's snackbar above the pill. */
val FloatingNavSnackbarOffset = 90.dp

private val BarHeight = 60.dp
private val ActionGap = 10.dp
private val PillShape = RoundedCornerShape(50)

/**
 * One motion for every change in the pill — chip, label, plus button and the pill's own width —
 * so they settle together. Critically damped: quick, but a bar that bounces reads as wobbly.
 */
private fun <T> navSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * The floating navigation: a dark pill with the tabs and, beside it, the round plus button for the
 * current tab's [primaryAction] (the pill takes the full width when there is none). Meant to be
 * aligned `BottomCenter` over the full-screen `NavHost`.
 */
@Composable
internal fun FloatingNavBar(
    tabs: List<NavTab>,
    currentRoute: String?,
    primaryAction: PrimaryAction?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = navPillColors()
    // The button keeps showing the last action while it animates out, so it shrinks away as itself.
    val lastAction = remember { LastValue(primaryAction) }
    if (primaryAction != null) lastAction.value = primaryAction
    val actionProgress = animateFloatAsState(
        targetValue = if (primaryAction != null) 1f else 0f,
        animationSpec = navSpring(),
        label = "navAction",
    )
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(BarHeight)
                .floatingShadow(PillShape)
                .clip(PillShape)
                .background(colors.container)
                .then(
                    if (colors.border != null) Modifier.border(1.dp, colors.border, PillShape) else Modifier,
                )
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                NavTabItem(
                    tab = tab,
                    selected = currentRoute == tab.screen.route,
                    colors = colors,
                    onClick = { onSelect(tab.screen) },
                )
            }
        }
        lastAction.value?.let { action ->
            PrimaryActionButton(
                action = action,
                progress = actionProgress,
                // Only the live action is a tour target; one that is leaving must not be spotlit.
                anchored = primaryAction != null,
            )
        }
    }
}

/**
 * The round plus button in a slot whose width — gap included — follows [progress], so the pill
 * (weighted to fill the rest) widens and narrows continuously instead of jumping. The button stays
 * pinned to the right edge and scales/fades with the same progress: the pill grows *into* it.
 */
@Composable
private fun PrimaryActionButton(action: PrimaryAction, progress: State<Float>, anchored: Boolean) {
    Box(
        modifier = Modifier
            // Read in the layout/draw phases only, so the animation never recomposes the bar.
            .layout { measurable, _ ->
                val gap = ActionGap.roundToPx()
                val size = BarHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                val width = ((gap + size) * progress.value).roundToInt()
                layout(width, size) { placeable.place(width - size, 0) }
            }
            .graphicsLayer {
                val p = progress.value
                scaleX = 0.4f + 0.6f * p
                scaleY = scaleX
                // Fades out ahead of the shrink, so it is gone before the pill reaches it.
                alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
            }
            .then(if (anchored) action.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier else Modifier)
            .floatingShadow(CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = anchored, onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = action.contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun NavTabItem(tab: NavTab, selected: Boolean, colors: NavPillColors, onClick: () -> Unit) {
    val chipColor by animateColorAsState(
        if (selected) colors.activeChip else Color.Transparent,
        animationSpec = navSpring(),
        label = "navChip",
    )
    val contentColor by animateColorAsState(
        if (selected) colors.activeContent else colors.inactiveIcon,
        animationSpec = navSpring(),
        label = "navContent",
    )
    Row(
        modifier = Modifier
            .then(tab.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier)
            .height(46.dp)
            .clip(PillShape)
            .background(chipColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.unselectedIcon,
            // The label only shows on the active tab, so the icon always carries the name for a11y.
            contentDescription = if (selected) null else tab.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        // Expanding the label is what widens the chip; the neighbours glide aside with it.
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(navSpring(), expandFrom = Alignment.Start) + fadeIn(navSpring()),
            exit = shrinkHorizontally(navSpring(), shrinkTowards = Alignment.Start) + fadeOut(navSpring()),
        ) {
            Text(
                tab.label,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                ),
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** A plain (non-snapshot) holder: remembering a value must not itself trigger a recomposition. */
internal class LastValue<T>(var value: T)

private fun Modifier.floatingShadow(shape: Shape) =
    shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)

private class NavPillColors(
    val container: Color,
    val border: Color?,
    val activeChip: Color,
    val activeContent: Color,
    val inactiveIcon: Color,
)

/**
 * The pill is dark in both shades: on Ink it is a raised `surfaceVariant` with a hairline, on Paper
 * it is the `onSurface` ink itself — so the two map *different* roles, hence [LocalInkTheme].
 */
@Composable
private fun navPillColors(): NavPillColors {
    val scheme = MaterialTheme.colorScheme
    return if (LocalInkTheme.current) {
        NavPillColors(
            container = scheme.surfaceVariant,
            border = scheme.outlineVariant,
            activeChip = scheme.onSurface,
            activeContent = scheme.background,
            inactiveIcon = scheme.onSurfaceVariant,
        )
    } else {
        NavPillColors(
            container = scheme.onSurface,
            border = null,
            activeChip = scheme.background,
            activeContent = scheme.onSurface,
            inactiveIcon = scheme.outline,
        )
    }
}
