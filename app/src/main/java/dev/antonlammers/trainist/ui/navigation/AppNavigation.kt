package dev.antonlammers.trainist.ui.navigation

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import dev.antonlammers.trainist.ui.overview.OverviewScreen
import dev.antonlammers.trainist.ui.settings.SettingsScreen
import dev.antonlammers.trainist.ui.stats.StatsScreen
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
}

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

// Active tab uses the filled (Rounded) icon variant, inactive the outline variant. A @Composable
// function (not a top-level val) since the labels need stringResource() and must reflect the
// current app locale on every composition, not just whichever locale was active when the
// containing class first loaded.
@Composable
private fun bottomNavItems() = listOf(
    BottomNavItem(Screen.Overview, stringResource(R.string.nav_overview_label), Icons.Rounded.Restaurant, Icons.Outlined.Restaurant),
    BottomNavItem(Screen.Workout, stringResource(R.string.templates_title), Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    BottomNavItem(Screen.Stats, stringResource(R.string.stats_title), Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    BottomNavItem(Screen.Settings, stringResource(R.string.settings_title), Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@ExperimentalGetImage
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    openWorkoutSession: Boolean = false,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navItems = bottomNavItems()
    val showBottomNav = navItems.any { it.screen.route == currentRoute }

    // Tapping a rest-timer notification relaunches the app with this flag set — jump straight
    // into the live session (which resumes the active one regardless of the templateId argument).
    LaunchedEffect(openWorkoutSession) {
        if (openWorkoutSession) navController.navigate(Screen.WorkoutSession.start(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.weight(1f),
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
        }
        if (showBottomNav) {
            // Flat nav bar: hairline top border replaces Material's tonal elevation.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                navItems.forEach { item ->
                    val selected = currentRoute == item.screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // Icon-only nav: labels are dropped (the pictograms are self-explanatory);
                        // the label text lives on as the icon's contentDescription for a11y.
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
        }
    }
}
