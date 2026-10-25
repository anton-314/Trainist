package dev.antonlammers.trainist.ui.navigation

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.addfood.AddFoodScreen
import dev.antonlammers.trainist.ui.addfood.BarcodeScannerScreen
import dev.antonlammers.trainist.ui.bmr.BmrCalculatorScreen
import dev.antonlammers.trainist.ui.overview.OverviewScreen
import dev.antonlammers.trainist.ui.goals.GoalsScreen
import dev.antonlammers.trainist.ui.help.HelpScreen
import dev.antonlammers.trainist.ui.settings.SettingsScreen
import dev.antonlammers.trainist.ui.stats.StatsScreen
import dev.antonlammers.trainist.ui.tutorial.LocalTutorialAnchors
import dev.antonlammers.trainist.ui.tutorial.TutorialAnchors
import dev.antonlammers.trainist.ui.tutorial.TutorialOverlay
import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import dev.antonlammers.trainist.ui.tutorial.TutorialViewModel
import dev.antonlammers.trainist.ui.workout.ExerciseCatalogScreen
import dev.antonlammers.trainist.ui.workout.ExerciseDetailScreen
import dev.antonlammers.trainist.ui.workout.TemplateEditorScreen
import dev.antonlammers.trainist.ui.workout.TemplatesScreen
import dev.antonlammers.trainist.ui.workout.WorkoutHistoryScreen
import dev.antonlammers.trainist.ui.workout.WorkoutSessionScreen
import java.time.LocalDate

sealed class Screen(val route: String) {
    object Overview : Screen("overview")
    object AddFood : Screen("add_food/{date}") {
        fun withDate(date: LocalDate) = "add_food/$date"
    }
    object Workout : Screen("workout")
    object WorkoutHistory : Screen("workout_history")
    object ExerciseCatalog : Screen("exercise_catalog")
    object ExerciseDetail : Screen("exercise_detail/{exerciseStableId}") {
        fun forExercise(stableId: String) = "exercise_detail/$stableId"
    }
    object TemplateEditor : Screen("template_editor/{templateId}") {
        /** id 0 opens the editor for a brand-new template. */
        fun forTemplate(templateId: Long) = "template_editor/$templateId"
    }
    object WorkoutSession : Screen("workout_session?templateId={templateId}") {
        /** templateId 0 starts an empty session; a running session is always resumed regardless. */
        fun start(templateId: Long = 0L) = "workout_session?templateId=$templateId"
    }
    object BarcodeScanner : Screen("barcode_scanner")
    object Stats : Screen("stats")
    object Settings : Screen("settings")

    // Settings and its sub-screens — absent from bottomNavItems, so the bar hides itself. Backup and
    // language are bottom sheets on the hub instead; a screen is for a real form (the goal editor)
    // or long-form reading (the Help Center).
    object Goals : Screen("goals")
    object Help : Screen("help")

    // Reached from GoalsScreen ("Kalorienbedarf berechnen") and hosted inline by onboarding's
    // local step machine — this route exists only for the Settings-triggered entry point.
    object BmrCalculator : Screen("bmr_calculator")
}

// Active tab uses the filled (Rounded) icon variant, inactive the outline variant. A @Composable
// function (not a top-level val) since the labels need stringResource() and must reflect the
// current app locale on every composition, not just whichever locale was active when the
// containing class first loaded. Settings is no tab: it is reached from the Overview header.
@Composable
private fun bottomNavItems() = listOf(
    NavTab(Screen.Overview, stringResource(R.string.nav_overview_label), Icons.Rounded.Restaurant, Icons.Outlined.Restaurant),
    NavTab(Screen.Workout, stringResource(R.string.templates_title), Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    NavTab(Screen.Stats, stringResource(R.string.stats_title), Icons.Rounded.BarChart, Icons.Outlined.BarChart, TutorialTarget.STATS_TAB),
)

/** Tab ↔ tab cross-fade — short, so the content keeps pace with the pill reacting to the tap. */
private const val TAB_FADE_MS = 220

/** Navigation-compose's own default cross-fade, kept for pushed screens. */
private const val SCREEN_FADE_MS = 700

private val tabRoutes = setOf(Screen.Overview.route, Screen.Workout.route, Screen.Stats.route)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition =
    fadeIn(tween(if (isTabSwitch()) TAB_FADE_MS else SCREEN_FADE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition =
    fadeOut(tween(if (isTabSwitch()) TAB_FADE_MS else SCREEN_FADE_MS))

@ExperimentalGetImage
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    openWorkoutSession: Boolean = false,
    tutorialViewModel: TutorialViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navItems = bottomNavItems()
    val showBottomNav = navItems.any { it.screen.route == currentRoute }

    // The guided tour runs *over* this navigation host: it owns the tab the tour is on, and the
    // anchors registry every screen below reports its highlightable elements to.
    val tutorialStep by tutorialViewModel.step.collectAsStateWithLifecycle()
    val tutorialAnchors = remember { TutorialAnchors() }
    // Tab screens register what the shell's plus button does for them (see PrimaryAction).
    val primaryActionHost = remember { PrimaryActionHost() }
    // Written before the screens below read it in their tutorialAnchor modifiers, so an element
    // sitting below the fold scrolls itself into the spotlight in the same frame.
    tutorialAnchors.currentTarget = tutorialStep?.target

    // Tapping a rest-timer notification relaunches the app with this flag set — jump straight
    // into the live session (which resumes the active one regardless of the templateId argument).
    LaunchedEffect(openWorkoutSession) {
        if (openWorkoutSession) navController.navigate(Screen.WorkoutSession.start(0))
    }

    // Each step names the tab it plays on; switching there is the tour's job, not the user's. Uses
    // the same tab-switch semantics as the bottom bar, so the tour leaves no back stack behind.
    LaunchedEffect(tutorialStep) {
        val route = tutorialStep?.route ?: return@LaunchedEffect
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalTutorialAnchors provides tutorialAnchors,
            LocalPrimaryActionHost provides primaryActionHost,
        ) {
            // The NavHost fills the whole screen; the floating pill overlays it, so every tab
            // screen leaves FloatingNavClearance below its content.
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Overview.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { screenEnter() },
                    exitTransition = { screenExit() },
                    popEnterTransition = { screenEnter() },
                    popExitTransition = { screenExit() },
                ) {
                    composable(Screen.Overview.route) { OverviewScreen(navController) }
                    composable(
                        route = Screen.AddFood.route,
                        arguments = listOf(navArgument("date") { type = NavType.StringType }),
                    ) { AddFoodScreen(navController) }
                    composable(Screen.Workout.route) { TemplatesScreen(navController) }
                    composable(Screen.WorkoutHistory.route) { WorkoutHistoryScreen(navController) }
                    composable(Screen.ExerciseCatalog.route) { ExerciseCatalogScreen(navController) }
                    composable(
                        route = Screen.ExerciseDetail.route,
                        arguments = listOf(navArgument("exerciseStableId") { type = NavType.StringType }),
                    ) { ExerciseDetailScreen(navController) }
                    composable(
                        route = Screen.TemplateEditor.route,
                        arguments = listOf(navArgument("templateId") { type = NavType.LongType }),
                    ) { TemplateEditorScreen(navController) }
                    composable(
                        route = Screen.WorkoutSession.route,
                        arguments = listOf(
                            navArgument("templateId") { type = NavType.LongType; defaultValue = 0L },
                        ),
                    ) { WorkoutSessionScreen(navController) }
                    composable(Screen.BarcodeScanner.route) { BarcodeScannerScreen(navController) }
                    composable(Screen.Stats.route) { StatsScreen(navController) }
                    composable(Screen.Settings.route) { SettingsScreen(navController) }
                    composable(Screen.Goals.route) { GoalsScreen(navController) }
                    composable(Screen.Help.route) {
                        // The tour's ViewModel is the activity-scoped one held above; a
                        // hiltViewModel() inside this destination would resolve against the
                        // back-stack entry and start a second, unobserved tour.
                        HelpScreen(navController, onStartTutorial = tutorialViewModel::start)
                    }
                    composable(Screen.BmrCalculator.route) { BmrCalculatorScreen(navController) }
                }
                // While the bar slides out towards a pushed screen it keeps showing the tab it
                // left, rather than deselecting every chip on the way out.
                val lastTabRoute = remember { LastValue(currentRoute) }
                if (showBottomNav) lastTabRoute.value = currentRoute
                AnimatedVisibility(
                    visible = showBottomNav,
                    enter = slideInVertically(tween(TAB_FADE_MS)) { it / 2 } + fadeIn(tween(TAB_FADE_MS)),
                    exit = slideOutVertically(tween(TAB_FADE_MS)) { it / 2 } + fadeOut(tween(TAB_FADE_MS)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    FloatingNavBar(
                        tabs = navItems,
                        currentRoute = lastTabRoute.value,
                        primaryAction = primaryActionHost.actionFor(currentRoute),
                        onSelect = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }

        tutorialStep?.let { step ->
            TutorialOverlay(
                step = step,
                anchors = tutorialAnchors,
                onNext = tutorialViewModel::next,
                onBack = tutorialViewModel::back,
                onSkip = tutorialViewModel::skip,
            )
        }
    }
}
