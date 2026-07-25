package dev.antonlammers.trainist.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.bmr.BmrCalculatorViewModel
import dev.antonlammers.trainist.ui.bmr.BmrCalculatorWizard
import dev.antonlammers.trainist.ui.data.DataViewModel
import dev.antonlammers.trainist.ui.data.toDisplayString
import dev.antonlammers.trainist.ui.goals.MacroGoalsEditor
import dev.antonlammers.trainist.ui.goals.MacroGoalsPrefill
import dev.antonlammers.trainist.ui.goals.GoalsViewModel

/** The three steps of the first-run flow: the welcome chooser, the BMR wizard, and the review form. */
private enum class OnboardingStep { Welcome, BmrWizard, GoalsReview }

/**
 * First-launch welcome flow. Shows the app name + logo and three ways in: quick-start via backup
 * import, start empty, or the featured "set up goals" path — a mandatory walk through the
 * Mifflin-St Jeor calculator ([BmrCalculatorWizard]) that lands on an editable goals-review form
 * ([MacroGoalsEditor]) pre-filled with the calculator's result. Every path calls [onFinished].
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    dataViewModel: DataViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
    bmrViewModel: BmrCalculatorViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var pendingPrefill by remember { mutableStateOf<MacroGoalsPrefill?>(null) }

    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(
            dataViewModel = dataViewModel,
            onImported = onFinished,
            onStartEmpty = onFinished,
            onOpenGuide = { step = OnboardingStep.BmrWizard },
        )

        OnboardingStep.BmrWizard -> BmrCalculatorWizard(
            viewModel = bmrViewModel,
            onExitToStart = { step = OnboardingStep.Welcome },
            onComplete = { result ->
                pendingPrefill = MacroGoalsPrefill(result.goalKcal, result.proteinG, result.carbsG, result.fatG)
                step = OnboardingStep.GoalsReview
            },
        )

        OnboardingStep.GoalsReview -> GoalsReviewStep(
            goalsViewModel = goalsViewModel,
            prefill = pendingPrefill,
            onBack = { step = OnboardingStep.Welcome },
            onOpenBmrCalculator = { step = OnboardingStep.BmrWizard },
            onDone = onFinished,
        )
    }
}

@Composable
private fun WelcomeStep(
    dataViewModel: DataViewModel,
    onImported: () -> Unit,
    onStartEmpty: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val dataState by dataViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Rasterize the launcher icon: on API 26+ R.mipmap.ic_launcher_round resolves to an
    // AdaptiveIconDrawable, which Compose's painterResource cannot decode (it crashes). Loading the
    // drawable and drawing it into a bitmap works for both adaptive and legacy icons.
    val context = LocalContext.current
    val logo = remember {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(width = 288, height = 288)
            .asImageBitmap()
    }

    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            snackbar.showSnackbar(it.toDisplayString(context))
            dataViewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { dataViewModel.import(it.toString(), onSuccess = onImported) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_prefix),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            if (dataState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            OptionCard(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.onboarding_quickstart_title),
                subtitle = stringResource(R.string.onboarding_quickstart_subtitle),
                enabled = !dataState.isLoading,
                onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
            )
            Spacer(Modifier.height(12.dp))
            OptionCard(
                icon = Icons.Rounded.RocketLaunch,
                title = stringResource(R.string.onboarding_start_empty_title),
                subtitle = stringResource(R.string.onboarding_start_empty_subtitle),
                enabled = !dataState.isLoading,
                onClick = onStartEmpty,
            )
            Spacer(Modifier.height(12.dp))
            FeaturedOptionCard(
                icon = Icons.Rounded.Calculate,
                badge = stringResource(R.string.onboarding_setup_goals_recommended_badge),
                title = stringResource(R.string.onboarding_setup_goals_title),
                subtitle = stringResource(R.string.onboarding_setup_goals_subtitle),
                enabled = !dataState.isLoading,
                onClick = onOpenGuide,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A tappable option: neutral icon chip + title + one-line explanation + chevron. */
@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * The promoted option: filled accent card with a mono-uppercase "recommended" badge, so new users
 * instinctively pick the featured path (the BMR calculator) over the two neutral [OptionCard]s.
 */
@Composable
private fun FeaturedOptionCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * The goals-review step: reached only once the BMR wizard has produced a result. Hosts the shared
 * [MacroGoalsEditor] pre-filled with that result, so it's reviewable/editable before saving —
 * mirrors the Settings goals editor's shell (title + back + skip + scrollable form).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsReviewStep(
    goalsViewModel: GoalsViewModel,
    prefill: MacroGoalsPrefill?,
    onBack: () -> Unit,
    onOpenBmrCalculator: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val goal by goalsViewModel.goal.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_setup_goals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.onboarding_guide_skip_button)) }
                },
            )
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
            MacroGoalsEditor(
                goal = goal,
                onSave = { goalsViewModel.save(it); onDone() },
                onOpenBmrCalculator = onOpenBmrCalculator,
                saveButtonLabel = stringResource(R.string.onboarding_guide_save_button),
                prefill = prefill,
                headerContent = {
                    Text(
                        stringResource(R.string.onboarding_guide_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}
