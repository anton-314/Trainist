package dev.antonlammers.trainist.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.antonlammers.trainist.ui.theme.LocalInkTheme
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import dev.antonlammers.trainist.ui.tutorial.tutorialAnchor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One tab of the floating pill. */
internal data class NavTab(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Set for the tabs the guided tour points at; null for the ones it reaches by content instead. */
    val tutorialTarget: TutorialTarget? = null,
    /**
     * Whether this tab shows the round plus button. Declared here rather than inferred from the
     * screen registering a [PrimaryAction], so the pill's geometry follows the tapped tab in the very
     * same frame — a registration only lands a frame after the incoming screen first composes.
     */
    val hasPrimaryAction: Boolean = false,
)

/** Clearance a tab screen leaves below its last item so it can scroll out from under the pill. */
val FloatingNavClearance = 110.dp

/** Lifts a tab screen's snackbar above the pill. */
val FloatingNavSnackbarOffset = 90.dp

private val BarHeight = 60.dp
private val ChipHeight = 46.dp
private val ActionGap = 10.dp
private val PillShape = RoundedCornerShape(50)
private val ShadowElevation = 12.dp

/**
 * One motion for every change in the pill — focus, label, plus button and the pill's own width —
 * so they settle together. Critically damped: quick, but a bar that bounces reads as wobbly.
 */
private fun <T> navSpring(stiffness: Float = Spring.StiffnessMediumLow) = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = stiffness,
)

/**
 * The focus's trailing edge follows a little softer than its leading edge, so on its way to the
 * next tab it stretches out and pulls itself back together, instead of sliding over as a rigid block.
 */
private const val TRAILING_EDGE_STIFFNESS = 260f

/**
 * The floating navigation: a dark pill with the tabs and, beside it, the round plus button for the
 * current tab's [primaryAction] (the pill takes the full width on a tab without one). Meant to be
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
    val selectedIndex = tabs.indexOfFirst { it.screen.route == currentRoute }
    val showsAction = tabs.getOrNull(selectedIndex)?.hasPrimaryAction == true
    // The button keeps showing the last action while it animates out (and for the frame before the
    // incoming tab has registered its own), so it never blinks empty.
    val lastAction = remember { LastValue(primaryAction) }
    if (primaryAction != null) lastAction.value = primaryAction
    val actionProgress = animateFloatAsState(
        targetValue = if (showsAction) 1f else 0f,
        animationSpec = navSpring(),
        label = "navAction",
    )
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavPill(
            tabs = tabs,
            selectedIndex = selectedIndex,
            colors = colors,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        lastAction.value?.let { action ->
            PrimaryActionButton(
                action = action,
                progress = actionProgress,
                // Only the live action is clickable and a tour target; a leaving one is decoration.
                live = showsAction && primaryAction != null,
            )
        }
    }
}

/**
 * The pill and its single focus indicator. The tabs are laid out twice, on top of each other and
 * identically: once in the inactive colour with the focus cut *out*, once in the active colour
 * clipped *to* the focus. So a tab turns active exactly where the focus covers it, pixel for pixel,
 * while the focus slides — no colour cross-fade that lags behind it, never an empty highlight.
 *
 * The focus is drawn from the tabs' *live* bounds, so it always hugs a chip whose label is still
 * unfolding, and its two edges travel between tabs independently (see [TRAILING_EDGE_STIFFNESS]).
 */
@Composable
private fun NavPill(
    tabs: List<NavTab>,
    selectedIndex: Int,
    colors: NavPillColors,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Left/right x of every tab, relative to the rows; written on placement, read when drawing.
    val tabLefts = remember(tabs.size) { mutableStateListOf(*Array(tabs.size) { Float.NaN }) }
    val tabRights = remember(tabs.size) { mutableStateListOf(*Array(tabs.size) { Float.NaN }) }
    // The focus edges as fractional tab indices: 1.5 = halfway between the second and third tab.
    val start = selectedIndex.coerceAtLeast(0).toFloat()
    val leftEdge = remember { Animatable(start) }
    val rightEdge = remember { Animatable(start) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val target = selectedIndex.toFloat()
        val movingRight = target > rightEdge.value
        val leading = navSpring<Float>()
        val trailing = navSpring<Float>(TRAILING_EDGE_STIFFNESS)
        launch { leftEdge.animateTo(target, if (movingRight) trailing else leading) }
        launch { rightEdge.animateTo(target, if (movingRight) leading else trailing) }
    }
    // Read only from draw lambdas, so the slide redraws the pill without recomposing it.
    val focusPath: DrawScope.() -> Path? = {
        if (selectedIndex < 0 || tabLefts.any { it.isNaN() } || tabRights.any { it.isNaN() }) {
            null
        } else {
            val left = interpolateEdge(tabLefts, leftEdge.value)
            val right = interpolateEdge(tabRights, rightEdge.value)
            val height = ChipHeight.toPx()
            val top = (size.height - height) / 2f
            Path().apply {
                addRoundRect(RoundRect(left, top, right, top + height, CornerRadius(height / 2f)))
            }
        }
    }

    Box(
        modifier = modifier
            .height(BarHeight)
            .shadow(ShadowElevation, PillShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(PillShape)
            .background(colors.container)
            .then(
                if (colors.border != null) Modifier.border(1.dp, colors.border, PillShape) else Modifier,
            )
            .padding(horizontal = 6.dp),
    ) {
        // The real tabs: inactive look, clickable, carrying semantics and tour anchors.
        NavTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            contentColor = colors.inactiveIcon,
            focused = false,
            modifier = Modifier.drawWithContent {
                val focus = focusPath()
                if (focus == null) {
                    drawContent()
                } else {
                    drawPath(focus, colors.focus)
                    clipPath(focus, ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
            },
            tabModifier = { index, tab ->
                Modifier
                    .onPlaced { coordinates ->
                        val bounds = coordinates.boundsInParent()
                        tabLefts[index] = bounds.left
                        tabRights[index] = bounds.right
                    }
                    .then(tab.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier)
                    .clip(PillShape)
                    // The sliding focus is the feedback; a ripple under it would only smudge it.
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab.screen) },
                    )
            },
        )
        // The same tabs in their focused look, visible only through the focus. Pure decoration.
        NavTabRow(
            tabs = tabs,
            selectedIndex = selectedIndex,
            contentColor = colors.activeContent,
            focused = true,
            modifier = Modifier
                .clearAndSetSemantics {}
                .drawWithContent {
                    val focus = focusPath() ?: return@drawWithContent
                    clipPath(focus) { this@drawWithContent.drawContent() }
                },
        )
    }
}

/** The x at fractional tab index [position] along [edges], linearly between its two neighbours. */
private fun interpolateEdge(edges: List<Float>, position: Float): Float {
    val clamped = position.coerceIn(0f, edges.lastIndex.toFloat())
    val lower = clamped.toInt()
    val upper = (lower + 1).coerceAtMost(edges.lastIndex)
    return lerp(edges[lower], edges[upper], clamped - lower)
}

/**
 * One layer of the pill's tabs. Both layers must lay out identically — same tabs, same unfolding
 * label — which is why the only differences are colour, icon style and the per-tab [tabModifier].
 */
@Composable
private fun NavTabRow(
    tabs: List<NavTab>,
    selectedIndex: Int,
    contentColor: Color,
    focused: Boolean,
    modifier: Modifier = Modifier,
    tabModifier: @Composable (index: Int, tab: NavTab) -> Modifier = { _, _ -> Modifier },
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Row(
                modifier = tabModifier(index, tab)
                    .height(ChipHeight)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (focused) tab.selectedIcon else tab.unselectedIcon,
                    // The label only shows on the active tab, so the icon always carries the name.
                    contentDescription = if (selected) null else tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                // Unfolding the label is what widens the chip; the neighbours glide aside with it.
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(navSpring(), expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(navSpring(), shrinkTowards = Alignment.Start),
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
                        softWrap = false,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The round plus button in a slot whose width — gap included — follows [progress], so the pill
 * (weighted to fill the rest) widens and narrows continuously instead of jumping. The button is
 * always exactly as large as the room left beside the gap, centred in it: it shrinks *into* its
 * slot as the pill grows, never overlaps the pill, and needs no transparency to disappear.
 */
@Composable
private fun PrimaryActionButton(action: PrimaryAction, progress: State<Float>, live: Boolean) {
    Box(
        modifier = Modifier
            // Read in the layout/draw phases only, so the animation never recomposes the bar.
            .layout { measurable, _ ->
                val gap = ActionGap.roundToPx()
                val size = BarHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                val width = ((gap + size) * progress.value).roundToInt()
                // Centred in the room right of the gap; the scale below fits it into that room.
                layout(width, size) { placeable.place(gap + (width - gap - size) / 2, 0) }
            }
            .then(if (live) action.tutorialTarget?.let { Modifier.tutorialAnchor(it) } ?: Modifier else Modifier)
            // Shadow, clip and scale on ONE layer: animating a layer *around* a shadowed one renders
            // it offscreen at the button's own size and cuts its shadow off square.
            .graphicsLayer {
                val gap = ActionGap.toPx()
                val size = BarHeight.toPx()
                val scale = (((gap + size) * progress.value - gap) / size).coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                // Fades only its last stretch, so it never ends as a stray dot beside the pill.
                alpha = (scale / 0.35f).coerceAtMost(1f)
                shadowElevation = ShadowElevation.toPx() * scale
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
                shape = CircleShape
                clip = true
            }
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = live, onClick = action.onClick),
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

/** A plain (non-snapshot) holder: remembering a value must not itself trigger a recomposition. */
internal class LastValue<T>(var value: T)

private class NavPillColors(
    val container: Color,
    val border: Color?,
    val focus: Color,
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
            focus = scheme.onSurface,
            activeContent = scheme.background,
            inactiveIcon = scheme.onSurfaceVariant,
        )
    } else {
        NavPillColors(
            container = scheme.onSurface,
            border = null,
            focus = scheme.background,
            activeContent = scheme.onSurface,
            inactiveIcon = scheme.outline,
        )
    }
}
