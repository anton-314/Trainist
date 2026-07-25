package dev.antonlammers.trainist.ui.bmr

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.antonlammers.trainist.ui.goals.BMR_RESULT_CARBS
import dev.antonlammers.trainist.ui.goals.BMR_RESULT_FAT
import dev.antonlammers.trainist.ui.goals.BMR_RESULT_KCAL
import dev.antonlammers.trainist.ui.goals.BMR_RESULT_PROTEIN

/**
 * Settings-reachable host for [BmrCalculatorWizard] (`Screen.BmrCalculator`, pushed from
 * `GoalsScreen`). Hands its result back via the previous back-stack entry's savedStateHandle —
 * the same round-trip pattern the barcode scanner uses to return a scanned code to `AddFoodScreen`.
 */
@Composable
fun BmrCalculatorScreen(
    navController: NavController,
    viewModel: BmrCalculatorViewModel = hiltViewModel(),
) {
    BmrCalculatorWizard(
        viewModel = viewModel,
        onExitToStart = { navController.popBackStack() },
        onComplete = { result ->
            navController.previousBackStackEntry?.savedStateHandle?.apply {
                set(BMR_RESULT_KCAL, result.goalKcal)
                set(BMR_RESULT_PROTEIN, result.proteinG)
                set(BMR_RESULT_CARBS, result.carbsG)
                set(BMR_RESULT_FAT, result.fatG)
            }
            navController.popBackStack()
        },
    )
}
