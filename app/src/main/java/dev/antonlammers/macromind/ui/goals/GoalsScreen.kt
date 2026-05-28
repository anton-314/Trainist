package dev.antonlammers.macromind.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macromind.domain.model.DailyGoal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navController: NavController,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val goal by viewModel.goal.collectAsStateWithLifecycle()

    var kcal by remember(goal) { mutableStateOf(goal.kcal.toInt().toString()) }
    var protein by remember(goal) { mutableStateOf(goal.proteinG.toInt().toString()) }
    var carbs by remember(goal) { mutableStateOf(goal.carbsG.toInt().toString()) }
    var fat by remember(goal) { mutableStateOf(goal.fatG.toInt().toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tagesziele") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalField(label = "Kalorien (kcal)", value = kcal, onValueChange = { kcal = it })
            GoalField(label = "Protein (g)", value = protein, onValueChange = { protein = it })
            GoalField(label = "Kohlenhydrate (g)", value = carbs, onValueChange = { carbs = it })
            GoalField(label = "Fett (g)", value = fat, onValueChange = { fat = it })
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.save(
                        DailyGoal(
                            kcal = kcal.toDoubleOrNull() ?: goal.kcal,
                            proteinG = protein.toDoubleOrNull() ?: goal.proteinG,
                            carbsG = carbs.toDoubleOrNull() ?: goal.carbsG,
                            fatG = fat.toDoubleOrNull() ?: goal.fatG,
                        )
                    )
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }
}

@Composable
private fun GoalField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
