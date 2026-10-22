package dev.antonlammers.trainist.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antonlammers.trainist.ui.theme.LocalInkTheme
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import dev.antonlammers.trainist.ui.tutorial.tutorialAnchor

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
private val PillShape = RoundedCornerShape(50)

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
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        primaryAction?.let { action ->
            Box(
                modifier = Modifier
                    .size(BarHeight)
                    .then(action.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier)
                    .floatingShadow(CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = action.onClick),
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
    }
}

@Composable
private fun NavTabItem(tab: NavTab, selected: Boolean, colors: NavPillColors, onClick: () -> Unit) {
    val chipColor by animateColorAsState(
        if (selected) colors.activeChip else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "navChip",
    )
    val contentColor by animateColorAsState(
        if (selected) colors.activeContent else colors.inactiveIcon,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "navContent",
    )
    Row(
        modifier = Modifier
            .then(tab.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier)
            .height(46.dp)
            .clip(PillShape)
            .background(chipColor)
            .clickable(onClick = onClick)
            .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.unselectedIcon,
            // The label only shows on the active tab, so the icon always carries the name for a11y.
            contentDescription = if (selected) null else tab.label,
            tint = contentColor,
            modifier = Modifier.size(if (selected) 20.dp else 22.dp),
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(
                tab.label,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

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
