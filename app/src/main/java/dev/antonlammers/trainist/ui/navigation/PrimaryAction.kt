package dev.antonlammers.trainist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget

/**
 * What the shell's round plus button next to the navigation pill does on the current tab. The tab
 * screen describes it; the shell only renders it — so the action can capture screen state (the
 * Overview's selected day) without the shell knowing anything about that screen.
 */
class PrimaryAction(
    val contentDescription: String,
    /** The tour step that spotlights the button while this action is the current one. */
    val tutorialTarget: TutorialTarget?,
    val onClick: () -> Unit,
)

/**
 * The registry between the tab screens and the shell: a screen registers its [PrimaryAction] under
 * its own route while it is composed, and the shell's button runs the one for the **current**
 * route. *Whether* a tab has the button at all is declared on its `NavTab`, so the pill's geometry
 * never waits for a registration; this registry only supplies what the button does.
 *
 * Keyed by route rather than "whoever registered last" because a tab switch keeps the outgoing
 * screen composed for the whole cross-fade: the button has to follow the route the user picked the
 * moment they tap, not the moment the old screen is finally disposed. For the same reason [clear]
 * only removes the action it is handed, never a newer registration under the same route.
 */
@Stable
class PrimaryActionHost {
    private val actions = mutableStateMapOf<String?, PrimaryAction>()

    fun actionFor(route: String?): PrimaryAction? = route?.let { actions[it] }

    fun register(route: String?, action: PrimaryAction) {
        actions[route] = action
    }

    fun clear(route: String?, action: PrimaryAction) {
        if (actions[route] === action) actions.remove(route)
    }
}

/** Provided by `AppNavigation`; a throwaway default keeps screens composable in previews/tests. */
val LocalPrimaryActionHost = staticCompositionLocalOf { PrimaryActionHost() }

/**
 * Registers the shell's plus-button action for as long as the calling screen is composed, under the
 * route of the navigation destination it is composed in. [onClick] is read through
 * [rememberUpdatedState], so the action always sees the screen's latest state without
 * re-registering on every recomposition.
 */
@Composable
fun ProvidePrimaryAction(
    contentDescription: String,
    tutorialTarget: TutorialTarget? = null,
    onClick: () -> Unit,
) {
    val host = LocalPrimaryActionHost.current
    // Inside a NavHost destination the owner is its back-stack entry; outside one (previews,
    // tests) there is no route, and the shell simply never shows the action.
    val route = (LocalViewModelStoreOwner.current as? NavBackStackEntry)?.destination?.route
    val latestOnClick by rememberUpdatedState(onClick)
    val action = remember(contentDescription, tutorialTarget) {
        PrimaryAction(contentDescription, tutorialTarget) { latestOnClick() }
    }
    DisposableEffect(host, route, action) {
        host.register(route, action)
        onDispose { host.clear(route, action) }
    }
}
