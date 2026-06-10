package dev.antonlammers.macrotrac.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.MacroCalculator
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import dev.antonlammers.macrotrac.util.normalizeDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navController: NavController,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val goal by viewModel.goal.collectAsStateWithLifecycle()

    var bodyWeight by remember { mutableStateOf("") }
    var kcal by remember(goal) { mutableStateOf(goal.kcal.toInt().toString()) }
    var protein by remember(goal) { mutableStateOf(goal.proteinG.toInt().toString()) }
    var carbs by remember(goal) { mutableStateOf(goal.carbsG.toInt().toString()) }
    var fat by remember(goal) { mutableStateOf(goal.fatG.toInt().toString()) }

    val bodyWeightKg = bodyWeight.normalizeDecimal().toDoubleOrNull()
    val kcalValue = kcal.normalizeDecimal().toDoubleOrNull()
    val proteinValue = protein.normalizeDecimal().toDoubleOrNull()
    val carbsValue = carbs.normalizeDecimal().toDoubleOrNull()
    val fatValue = fat.normalizeDecimal().toDoubleOrNull()

    val calculatedKcal = if (proteinValue != null && carbsValue != null && fatValue != null)
        MacroCalculator.kcalFromMacros(proteinValue, carbsValue, fatValue) else null

    val calculatedCarbs = if (kcalValue != null && proteinValue != null && fatValue != null)
        MacroCalculator.carbsFromKcalAndMacros(kcalValue, proteinValue, fatValue) else null

    val showWarning = kcalValue != null && calculatedKcal != null &&
        !MacroCalculator.isConsistent(kcalValue, proteinValue!!, carbsValue!!, fatValue!!)

    val kcalDelta = if (kcalValue != null && calculatedKcal != null)
        MacroCalculator.kcalDelta(kcalValue, proteinValue!!, carbsValue!!, fatValue!!) else null

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tagesziele") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Körpergewicht", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumericTextField(
                    value = bodyWeight,
                    onValueChange = { bodyWeight = it },
                    label = "Gewicht (kg)",
                    suffix = "kg",
                    modifier = Modifier.weight(1f),
                )
                if (bodyWeightKg != null) {
                    TextButton(onClick = {
                        protein = MacroCalculator.recommendedProteinG(bodyWeightKg).toInt().toString()
                        fat = MacroCalculator.recommendedFatG(bodyWeightKg).toInt().toString()
                    }) {
                        Text("Übernehmen")
                    }
                }
            }
            if (bodyWeightKg != null) {
                val recProtein = MacroCalculator.recommendedProteinG(bodyWeightKg).toInt()
                val recFat = MacroCalculator.recommendedFatG(bodyWeightKg).toInt()
                Text(
                    "Empfehlung: ${recProtein}g Protein · ${recFat}g Fett · Rest mit Kohlenhydraten auffüllen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            Text("Makros & Kalorien", style = MaterialTheme.typography.labelLarge)

            NumericTextField(
                label = "Protein (g)",
                value = protein,
                onValueChange = { protein = it },
                decimal = false,
                supportingText = "Empfehlung: 2,2g pro kg Körpergewicht",
                modifier = Modifier.fillMaxWidth(),
            )

            NumericTextField(
                label = "Fett (g)",
                value = fat,
                onValueChange = { fat = it },
                decimal = false,
                supportingText = "Empfehlung: 1g pro kg Körpergewicht",
                modifier = Modifier.fillMaxWidth(),
            )

            NumericTextField(
                label = "Kohlenhydrate (g)",
                value = carbs,
                onValueChange = { carbs = it },
                decimal = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (calculatedCarbs != null) {
                TextButton(
                    onClick = { carbs = calculatedCarbs.toInt().toString() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Aus kcal & Makros berechnen (→ ${calculatedCarbs.toInt()}g)")
                }
            }

            NumericTextField(
                label = "Kalorien (kcal)",
                value = kcal,
                onValueChange = { kcal = it },
                decimal = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (calculatedKcal != null) {
                TextButton(
                    onClick = { kcal = calculatedKcal.toInt().toString() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Aus Makros berechnen (→ ${calculatedKcal.toInt()} kcal)")
                }
            }

            AnimatedVisibility(visible = showWarning) {
                if (kcalDelta != null && calculatedKcal != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Kalorien stimmen nicht mit Makros überein",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                val sign = if (kcalDelta > 0) "+" else ""
                                Text(
                                    "Makros ergeben ${calculatedKcal.toInt()} kcal " +
                                        "(${sign}${kcalDelta.toInt()} kcal Unterschied)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.save(
                        DailyGoal(
                            kcal = kcal.normalizeDecimal().toDoubleOrNull() ?: goal.kcal,
                            proteinG = protein.normalizeDecimal().toDoubleOrNull() ?: goal.proteinG,
                            carbsG = carbs.normalizeDecimal().toDoubleOrNull() ?: goal.carbsG,
                            fatG = fat.normalizeDecimal().toDoubleOrNull() ?: goal.fatG,
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

