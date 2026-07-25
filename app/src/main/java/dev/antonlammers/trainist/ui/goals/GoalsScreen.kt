package dev.antonlammers.trainist.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.navigation.Screen
import kotlinx.coroutines.launch

/**
 * Daily-goals editor — reached from the settings hub. Goals are targets the Ernährung tab measures
 * against rather than app configuration, so the form gets a screen of its own instead of sitting on
 * top of the hub. Hosts the shared [MacroGoalsEditor] (also used by onboarding's goals-review step)
 * and receives a BMR-calculator result back from [Screen.BmrCalculator] via the NavController's
 * savedStateHandle — the same round-trip pattern the barcode scanner uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navController: NavController,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val goalsSavedMessage = stringResource(R.string.goals_saved_message)

    var bmrPrefill by remember { mutableStateOf<MacroGoalsPrefill?>(null) }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<Double?>(BMR_RESULT_KCAL, null)?.collect { kcal ->
            if (kcal != null) {
                bmrPrefill = MacroGoalsPrefill(
                    kcal = kcal,
                    proteinG = savedStateHandle.get<Double>(BMR_RESULT_PROTEIN) ?: return@collect,
                    carbsG = savedStateHandle.get<Double>(BMR_RESULT_CARBS) ?: return@collect,
                    fatG = savedStateHandle.get<Double>(BMR_RESULT_FAT) ?: return@collect,
                )
                listOf(BMR_RESULT_KCAL, BMR_RESULT_PROTEIN, BMR_RESULT_CARBS, BMR_RESULT_FAT)
                    .forEach { savedStateHandle.remove<Double>(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_section_header)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MacroGoalsEditor(
                goal = goal,
                onSave = {
                    viewModel.save(it)
                    scope.launch { snackbar.showSnackbar(goalsSavedMessage) }
                },
                onOpenBmrCalculator = { navController.navigate(Screen.BmrCalculator.route) },
                saveButtonLabel = stringResource(R.string.common_save),
                prefill = bmrPrefill,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal const val BMR_RESULT_KCAL = "bmr_result_kcal"
internal const val BMR_RESULT_PROTEIN = "bmr_result_protein"
internal const val BMR_RESULT_CARBS = "bmr_result_carbs"
internal const val BMR_RESULT_FAT = "bmr_result_fat"
