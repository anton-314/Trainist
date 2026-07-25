package dev.antonlammers.trainist.ui.bmr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.WeightGoal
import dev.antonlammers.trainist.ui.goals.GoalField

/**
 * The BMR/TDEE step machine's UI, shared by both hosts (onboarding's local step machine and the
 * pushed `Screen.BmrCalculator` route). Hardware/system back and the top-bar chevron both step
 * back via [BmrCalculatorViewModel.goBack]; at the first step that returns false and [onExitToStart]
 * takes over. [onComplete] fires only from the last step, once a full [BmrResult] exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmrCalculatorWizard(
    viewModel: BmrCalculatorViewModel,
    onExitToStart: () -> Unit,
    onComplete: (BmrResult) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun back() {
        if (!viewModel.goBack()) onExitToStart()
    }
    BackHandler(onBack = ::back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bmr_wizard_title)) },
                navigationIcon = {
                    IconButton(onClick = ::back) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onExitToStart) {
                        Text(stringResource(R.string.onboarding_guide_skip_button))
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
        ) {
            LinearProgressIndicator(
                progress = { (uiState.step.ordinal + 1) / BmrStep.entries.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState.step) {
                    BmrStep.SEX -> SexStep(uiState.sex, viewModel::setSex)
                    BmrStep.AGE -> AgeStep(uiState.ageInput, viewModel::setAgeInput)
                    BmrStep.HEIGHT -> HeightStep(uiState.heightInput, viewModel::setHeightInput)
                    BmrStep.WEIGHT -> WeightStep(uiState.weightInput, viewModel::setWeightInput)
                    BmrStep.ACTIVITY -> ActivityStep(uiState.activityLevel, viewModel::setActivityLevel)
                    BmrStep.GOAL -> GoalStep(uiState, viewModel)
                }
            }
            Spacer(Modifier.height(16.dp))
            val isLastStep = uiState.step == BmrStep.GOAL
            Button(
                onClick = {
                    if (isLastStep) {
                        viewModel.result(uiState)?.let(onComplete)
                    } else {
                        viewModel.goNext()
                    }
                },
                enabled = viewModel.canProceed(uiState),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    stringResource(if (isLastStep) R.string.bmr_calculate_button else R.string.bmr_next_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StepHeading(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun SexStep(selected: BiologicalSex?, onSelect: (BiologicalSex) -> Unit) {
    StepHeading(stringResource(R.string.bmr_step_sex_title))
    SelectableOption(
        title = stringResource(R.string.bmr_sex_male),
        selected = selected == BiologicalSex.MALE,
        onClick = { onSelect(BiologicalSex.MALE) },
    )
    SelectableOption(
        title = stringResource(R.string.bmr_sex_female),
        selected = selected == BiologicalSex.FEMALE,
        onClick = { onSelect(BiologicalSex.FEMALE) },
    )
}

@Composable
private fun AgeStep(value: String, onChange: (String) -> Unit) {
    StepHeading(stringResource(R.string.bmr_step_age_title))
    GoalField(
        label = stringResource(R.string.bmr_field_age_label),
        value = value,
        onValueChange = onChange,
        decimal = false,
        suffix = stringResource(R.string.bmr_field_age_suffix),
    )
}

@Composable
private fun HeightStep(value: String, onChange: (String) -> Unit) {
    StepHeading(stringResource(R.string.bmr_step_height_title))
    GoalField(
        label = stringResource(R.string.bmr_field_height_label),
        value = value,
        onValueChange = onChange,
        decimal = true,
        suffix = "cm",
    )
}

@Composable
private fun WeightStep(value: String, onChange: (String) -> Unit) {
    StepHeading(stringResource(R.string.bmr_step_weight_title))
    GoalField(
        label = stringResource(R.string.bmr_field_weight_label),
        value = value,
        onValueChange = onChange,
        decimal = true,
        suffix = "kg",
    )
}

@Composable
private fun ActivityStep(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    StepHeading(stringResource(R.string.bmr_step_activity_title))
    ActivityLevel.entries.forEach { level ->
        SelectableOption(
            title = level.label(),
            subtitle = level.description(),
            selected = selected == level,
            onClick = { onSelect(level) },
        )
    }
}

@Composable
private fun GoalStep(uiState: BmrCalculatorUiState, viewModel: BmrCalculatorViewModel) {
    StepHeading(stringResource(R.string.bmr_step_goal_title))
    WeightGoal.entries.forEach { goal ->
        SelectableOption(
            title = goal.label(),
            subtitle = goal.description(),
            selected = uiState.goal == goal,
            onClick = { viewModel.setGoal(goal) },
        )
    }
    viewModel.result(uiState)?.let { result ->
        BmrResultPreview(result)
    }
}

@Composable
private fun BmrResultPreview(result: BmrResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.bmr_result_kcal_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${result.goalKcal.toInt()} kcal", style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    R.string.bmr_result_macros,
                    result.proteinG.toInt(),
                    result.carbsG.toInt(),
                    result.fatG.toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tappable choice card used by the sex/activity/goal steps: title + optional subtitle + a
 * check mark when selected, accent border/background while selected. */
@Composable
private fun SelectableOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ActivityLevel.label(): String = stringResource(
    when (this) {
        ActivityLevel.SEDENTARY -> R.string.bmr_activity_sedentary_label
        ActivityLevel.LIGHT -> R.string.bmr_activity_light_label
        ActivityLevel.MODERATE -> R.string.bmr_activity_moderate_label
        ActivityLevel.ACTIVE -> R.string.bmr_activity_active_label
        ActivityLevel.VERY_ACTIVE -> R.string.bmr_activity_very_active_label
    },
)

@Composable
private fun ActivityLevel.description(): String = stringResource(
    when (this) {
        ActivityLevel.SEDENTARY -> R.string.bmr_activity_sedentary_description
        ActivityLevel.LIGHT -> R.string.bmr_activity_light_description
        ActivityLevel.MODERATE -> R.string.bmr_activity_moderate_description
        ActivityLevel.ACTIVE -> R.string.bmr_activity_active_description
        ActivityLevel.VERY_ACTIVE -> R.string.bmr_activity_very_active_description
    },
)

@Composable
private fun WeightGoal.label(): String = stringResource(
    when (this) {
        WeightGoal.LOSE -> R.string.bmr_goal_lose_label
        WeightGoal.MAINTAIN -> R.string.bmr_goal_maintain_label
        WeightGoal.GAIN -> R.string.bmr_goal_gain_label
    },
)

@Composable
private fun WeightGoal.description(): String = stringResource(
    when (this) {
        WeightGoal.LOSE -> R.string.bmr_goal_lose_description
        WeightGoal.MAINTAIN -> R.string.bmr_goal_maintain_description
        WeightGoal.GAIN -> R.string.bmr_goal_gain_description
    },
)
