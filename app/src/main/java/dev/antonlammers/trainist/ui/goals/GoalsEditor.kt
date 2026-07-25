package dev.antonlammers.trainist.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.MacroCalculator
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.util.normalizeDecimal

/** A calculator result to seed the macro/kcal fields with — see [MacroGoalsEditor]'s `prefill`. */
internal data class MacroGoalsPrefill(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/**
 * Shared daily-goals editor — body-weight-driven recommendations + macro/kcal fields + target
 * weight. Used by both [GoalsScreen] (Settings) and onboarding's goals-review step, which is why
 * every piece of copy/behaviour lives here once instead of being hand-duplicated per caller.
 *
 * [prefill], when non-null (a fresh BMR-calculator result), resets the kcal/protein/carbs/fat
 * fields to it; the fields then stay freely editable exactly as without a prefill — the actual
 * [onSave] only fires when the caller's Save button is tapped, so a calculator result is always
 * reviewable before it's persisted.
 */
@Composable
internal fun ColumnScope.MacroGoalsEditor(
    goal: DailyGoal,
    onSave: (DailyGoal) -> Unit,
    onOpenBmrCalculator: () -> Unit,
    saveButtonLabel: String,
    prefill: MacroGoalsPrefill? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    headerContent?.invoke(this)

    var bodyWeight by remember { mutableStateOf("") }
    var kcal by remember(goal, prefill) { mutableStateOf((prefill?.kcal ?: goal.kcal).toInt().toString()) }
    var protein by remember(goal, prefill) { mutableStateOf((prefill?.proteinG ?: goal.proteinG).toInt().toString()) }
    var carbs by remember(goal, prefill) { mutableStateOf((prefill?.carbsG ?: goal.carbsG).toInt().toString()) }
    var fat by remember(goal, prefill) { mutableStateOf((prefill?.fatG ?: goal.fatG).toInt().toString()) }
    var targetWeight by remember(goal) {
        mutableStateOf(goal.targetWeightKg?.let { formatWeight(it) } ?: "")
    }

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

    OutlinedButton(
        onClick = onOpenBmrCalculator,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.goals_bmr_calculator_button))
    }

    // Body weight — drives the protein/fat recommendations (not a goal itself).
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(stringResource(R.string.goals_body_weight_label))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumericTextField(
                value = bodyWeight,
                onValueChange = { bodyWeight = it },
                label = null,
                suffix = "kg",
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (bodyWeightKg != null) {
                TextButton(onClick = {
                    protein = MacroCalculator.recommendedProteinG(bodyWeightKg).toInt().toString()
                    fat = MacroCalculator.recommendedFatG(bodyWeightKg).toInt().toString()
                }) {
                    Text(stringResource(R.string.goals_body_weight_apply_button))
                }
            }
        }
    }
    if (bodyWeightKg != null) {
        val recProtein = MacroCalculator.recommendedProteinG(bodyWeightKg).toInt()
        val recFat = MacroCalculator.recommendedFatG(bodyWeightKg).toInt()
        Text(
            stringResource(R.string.goals_recommendation, recProtein, recFat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider()
    Text(stringResource(R.string.goals_macros_calories_header), style = MaterialTheme.typography.titleMedium)

    GoalField(
        label = stringResource(R.string.goals_protein_label),
        value = protein,
        onValueChange = { protein = it },
        accentColor = ProteinColor,
        supportingText = stringResource(R.string.goals_protein_supporting_text),
    )

    GoalField(
        label = stringResource(R.string.goals_fat_label),
        value = fat,
        onValueChange = { fat = it },
        accentColor = FatColor,
        supportingText = stringResource(R.string.goals_fat_supporting_text),
    )

    GoalField(
        label = stringResource(R.string.goals_carbs_label),
        value = carbs,
        onValueChange = { carbs = it },
        accentColor = CarbsColor,
    )
    if (calculatedCarbs != null) {
        TextButton(
            onClick = { carbs = calculatedCarbs.toInt().toString() },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.goals_carbs_calc_button, calculatedCarbs.toInt()))
        }
    }

    GoalField(
        label = stringResource(R.string.goals_kcal_label),
        value = kcal,
        onValueChange = { kcal = it },
        accentColor = CalorieColor,
    )
    if (calculatedKcal != null) {
        TextButton(
            onClick = { kcal = calculatedKcal.toInt().toString() },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.goals_kcal_calc_button, calculatedKcal.toInt()))
        }
    }

    AnimatedVisibility(visible = showWarning) {
        if (kcalDelta != null && calculatedKcal != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.goals_warning_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        val sign = if (kcalDelta > 0) "+" else ""
                        Text(
                            stringResource(R.string.goals_warning_detail, calculatedKcal.toInt(), sign, kcalDelta.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider()
    GoalField(
        label = stringResource(R.string.goals_target_weight_label),
        value = targetWeight,
        onValueChange = { targetWeight = it },
        decimal = true,
        suffix = "kg",
        supportingText = stringResource(R.string.goals_target_weight_supporting_text),
    )

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = {
            onSave(
                DailyGoal(
                    kcal = kcalValue ?: goal.kcal,
                    proteinG = proteinValue ?: goal.proteinG,
                    carbsG = carbsValue ?: goal.carbsG,
                    fatG = fatValue ?: goal.fatG,
                    // Blank clears the target (null).
                    targetWeightKg = targetWeight.normalizeDecimal().toDoubleOrNull(),
                    bmrProfile = goal.bmrProfile,
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(saveButtonLabel, style = MaterialTheme.typography.labelLarge)
    }
}
