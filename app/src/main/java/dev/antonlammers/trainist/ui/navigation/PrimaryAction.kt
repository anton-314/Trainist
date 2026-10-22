package dev.antonlammers.trainist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
 * The registry between the tab screens and the shell: a screen registers its [PrimaryAction] while
 * it is composed, the shell shows the button for whatever is registered (and nothing on a tab that
 * registers nothing, e.g. Statistik).
 *
 * During a tab switch both screens are briefly composed, and the incoming one may register before
 * the outgoing one is disposed — so [clear] only removes the action it is handed, never a newer one.
 */
@Stable
class PrimaryActionHost {
    var current: PrimaryAction? by mutableStateOf(null)
        private set

    fun register(action: PrimaryAction) {
        current = action
    }

    fun clear(action: PrimaryAction) {
        if (current === action) current = null
    }
}

/** Provided by `AppNavigation`; a throwaway default keeps screens composable in previews/tests. */
val LocalPrimaryActionHost = staticCompositionLocalOf { PrimaryActionHost() }

/**
 * Registers the shell's plus-button action for as long as the calling screen is composed. [onClick]
 * is read through [rememberUpdatedState], so the action always sees the screen's latest state
 * without re-registering on every recomposition.
 */
@Composable
fun ProvidePrimaryAction(
    contentDescription: String,
    tutorialTarget: TutorialTarget? = null,
    onClick: () -> Unit,
) {
    val host = LocalPrimaryActionHost.current
    val latestOnClick by rememberUpdatedState(onClick)
    val action = remember(contentDescription, tutorialTarget) {
        PrimaryAction(contentDescription, tutorialTarget) { latestOnClick() }
    }
    DisposableEffect(host, action) {
        host.register(action)
        onDispose { host.clear(action) }
    }
}
