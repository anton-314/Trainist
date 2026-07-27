package dev.antonlammers.trainist.ui.tutorial

import androidx.annotation.StringRes
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.navigation.Screen

/**
 * The UI element a tutorial step points at. A screen marks its element with
 * `Modifier.tutorialAnchor(target)`; the overlay looks the measured bounds up by this key, so the
 * step list and the screens stay decoupled — a screen never knows the tutorial's order or texts.
 */
enum class TutorialTarget {
    ADD_FOOD,
    DAY_SWITCH,
    CALORIE_RING,
    WEIGHT_CARD,
    START_WORKOUT,
    NEW_TEMPLATE,
    WORKOUT_HISTORY,
    STATS_TAB,
    SETTINGS_TAB,
}

/** How the spotlight cuts the target out of the scrim: round for icons/FABs, a pill for wide rows. */
enum class TutorialHighlight { CIRCLE, ROUNDED }

/**
 * The guided tour, in order. Each step names the tab it plays on, the element it highlights, and
 * its two texts — content as data (like `ui/help/HelpContent`), so adding a step is one entry plus
 * its strings, and [TutorialStepTest] can guard the things that only show up on a device.
 *
 * Steps are grouped by [route]: the tour walks each tab once, top to bottom, instead of bouncing
 * between tabs.
 */
enum class TutorialStep(
    val route: String,
    val target: TutorialTarget,
    val highlight: TutorialHighlight,
    @StringRes val title: Int,
    @StringRes val body: Int,
) {
    ADD_FOOD(
        route = Screen.Overview.route,
        target = TutorialTarget.ADD_FOOD,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_add_food_title,
        body = R.string.tutorial_add_food_body,
    ),
    DAY_SWITCH(
        route = Screen.Overview.route,
        target = TutorialTarget.DAY_SWITCH,
        highlight = TutorialHighlight.ROUNDED,
        title = R.string.tutorial_day_switch_title,
        body = R.string.tutorial_day_switch_body,
    ),
    CALORIE_RING(
        route = Screen.Overview.route,
        target = TutorialTarget.CALORIE_RING,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_calorie_ring_title,
        body = R.string.tutorial_calorie_ring_body,
    ),
    WEIGHT(
        route = Screen.Overview.route,
        target = TutorialTarget.WEIGHT_CARD,
        highlight = TutorialHighlight.ROUNDED,
        title = R.string.tutorial_weight_title,
        body = R.string.tutorial_weight_body,
    ),
    START_WORKOUT(
        route = Screen.Workout.route,
        target = TutorialTarget.START_WORKOUT,
        highlight = TutorialHighlight.ROUNDED,
        title = R.string.tutorial_start_workout_title,
        body = R.string.tutorial_start_workout_body,
    ),
    NEW_TEMPLATE(
        route = Screen.Workout.route,
        target = TutorialTarget.NEW_TEMPLATE,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_new_template_title,
        body = R.string.tutorial_new_template_body,
    ),
    WORKOUT_HISTORY(
        route = Screen.Workout.route,
        target = TutorialTarget.WORKOUT_HISTORY,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_history_title,
        body = R.string.tutorial_history_body,
    ),
    STATS(
        route = Screen.Stats.route,
        target = TutorialTarget.STATS_TAB,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_stats_title,
        body = R.string.tutorial_stats_body,
    ),
    SETTINGS(
        route = Screen.Settings.route,
        target = TutorialTarget.SETTINGS_TAB,
        highlight = TutorialHighlight.CIRCLE,
        title = R.string.tutorial_settings_title,
        body = R.string.tutorial_settings_body,
    );

    /** 1-based position, for the "3 / 9" progress label. */
    val number: Int get() = ordinal + 1

    val isLast: Boolean get() = ordinal == entries.lastIndex

    /** The step after this one, or null when the tour is over. */
    fun next(): TutorialStep? = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null when this is the first. */
    fun previous(): TutorialStep? = entries.getOrNull(ordinal - 1)

    companion object {
        val first: TutorialStep get() = entries.first()
        val count: Int get() = entries.size
    }
}
