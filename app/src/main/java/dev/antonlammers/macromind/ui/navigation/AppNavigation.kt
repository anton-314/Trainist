package dev.antonlammers.macromind.ui.navigation

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.antonlammers.macromind.ui.addfood.AddFoodScreen
import dev.antonlammers.macromind.ui.addfood.BarcodeScannerScreen
import dev.antonlammers.macromind.ui.data.DataScreen
import dev.antonlammers.macromind.ui.goals.GoalsScreen
import dev.antonlammers.macromind.ui.overview.OverviewScreen

sealed class Screen(val route: String) {
    object Overview : Screen("overview")
    object AddFood : Screen("add_food")
    object Goals : Screen("goals")
    object BarcodeScanner : Screen("barcode_scanner")
    object Data : Screen("data")
}

@ExperimentalGetImage
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Overview.route) {
        composable(Screen.Overview.route) { OverviewScreen(navController) }
        composable(Screen.AddFood.route) { AddFoodScreen(navController) }
        composable(Screen.Goals.route) { GoalsScreen(navController) }
        composable(Screen.BarcodeScanner.route) { BarcodeScannerScreen(navController) }
        composable(Screen.Data.route) { DataScreen(navController) }
    }
}
