package dev.antonlammers.trainist.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the tutorial's spotlight is allowed to point: the measured screen bounds of every
 * [TutorialTarget] that is currently on screen, plus the target the tour is asking for right now.
 *
 * This is UI geometry, so it deliberately lives in the composition rather than on
 * [TutorialViewModel] — the ViewModel owns *which* step is showing, this owns *where* it is. A
 * screen contributes to it without knowing anything about the tour by tagging its element with
 * [tutorialAnchor]; a target that scrolls away or whose screen is left simply unregisters, and the
 * overlay falls back to a centred bubble with no cut-out.
 */
@Stable
class TutorialAnchors {
    private val bounds = mutableStateMapOf<TutorialTarget, Rect>()

    /** The target the current step points at, or null while no tour is running. */
    var currentTarget: TutorialTarget? by mutableStateOf(null)
        internal set

    operator fun get(target: TutorialTarget): Rect? = bounds[target]

    internal fun register(target: TutorialTarget, rect: Rect) {
        bounds[target] = rect
    }

    internal fun forget(target: TutorialTarget) {
        bounds.remove(target)
    }
}

/**
 * The anchor registry visible to the screens below the overlay. Defaults to a throwaway instance so
 * every screen composes normally outside the app's navigation host (previews, UI tests).
 */
val LocalTutorialAnchors = staticCompositionLocalOf { TutorialAnchors() }

/**
 * Marks this element as the tutorial's [target]: reports its bounds to [LocalTutorialAnchors] on
 * every layout pass and scrolls itself into view when the tour reaches it (an element sitting just
 * below the fold would otherwise be spotlighted off-screen).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tutorialAnchor(target: TutorialTarget): Modifier {
    val anchors = LocalTutorialAnchors.current
    val requester = remember { BringIntoViewRequester() }

    DisposableEffect(anchors, target) {
        onDispose { anchors.forget(target) }
    }
    val isCurrent = anchors.currentTarget == target
    LaunchedEffect(isCurrent) {
        if (isCurrent) requester.bringIntoView()
    }

    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { anchors.register(target, it.boundsInRoot()) }
}
