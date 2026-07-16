package dev.antonlammers.trainist.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import dev.antonlammers.trainist.domain.MacroCalculator
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.data.DataViewModel
import dev.antonlammers.trainist.ui.goals.FieldLabel
import dev.antonlammers.trainist.ui.goals.GoalField
import dev.antonlammers.trainist.ui.goals.GoalsViewModel
import dev.antonlammers.trainist.ui.goals.formatWeight
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.util.normalizeDecimal

/** The two steps of the first-run flow: the welcome chooser and the optional goals guide. */
private enum class OnboardingStep { Welcome, Guide }

/**
 * First-launch welcome flow. Shows the app name + logo and three ways in (spec: quick-start via
 * backup import, start empty, or a short goals guide). Every path calls [onFinished] to leave the
 * flow. The goals guide is the designated home for future setup steps (height, kcal calculator).
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    dataViewModel: DataViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }

    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(
            dataViewModel = dataViewModel,
            onImported = onFinished,
            onStartEmpty = onFinished,
            onOpenGuide = { step = OnboardingStep.Guide },
        )

        OnboardingStep.Guide -> GuideStep(
            goalsViewModel = goalsViewModel,
            onBack = { step = OnboardingStep.Welcome },
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
            snackbar.showSnackbar(it)
            dataViewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { dataViewModel.import(it, onSuccess = onImported) }
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
                text = "Willkommen bei",
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
                text = "Wie möchtest du starten?",
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
                title = "Schnellstart",
                subtitle = "Ein vorhandenes Backup importieren und direkt loslegen.",
                enabled = !dataState.isLoading,
                onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
            )
            Spacer(Modifier.height(12.dp))
            OptionCard(
                icon = Icons.Rounded.RocketLaunch,
                title = "Einfach loslegen",
                subtitle = "Mit einer leeren App beginnen — Ziele kannst du jederzeit später setzen.",
                enabled = !dataState.isLoading,
                onClick = onStartEmpty,
            )
            Spacer(Modifier.height(12.dp))
            OptionCard(
                icon = Icons.Rounded.Flag,
                title = "Ziele einrichten",
                subtitle = "Ein kurzer Guide: Zielgewicht und deine Tagesziele festlegen.",
                enabled = !dataState.isLoading,
                onClick = onOpenGuide,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A tappable option: accent icon chip + title + one-line explanation + chevron. */
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
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
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
 * The goals guide: body-weight-driven recommendations, macro/kcal goals and an optional target
 * weight. Mirrors the Settings goal editor's logic (shared [GoalField]/[MacroCalculator]) but as a
 * friendly first-run step ending in "Speichern & loslegen". Persists via [GoalsViewModel.save].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideStep(
    goalsViewModel: GoalsViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val goal by goalsViewModel.goal.collectAsStateWithLifecycle()

    var bodyWeight by remember { mutableStateOf("") }
    var kcal by remember(goal) { mutableStateOf(goal.kcal.toInt().toString()) }
    var protein by remember(goal) { mutableStateOf(goal.proteinG.toInt().toString()) }
    var carbs by remember(goal) { mutableStateOf(goal.carbsG.toInt().toString()) }
    var fat by remember(goal) { mutableStateOf(goal.fatG.toInt().toString()) }
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

    fun persistAndFinish() {
        goalsViewModel.save(
            DailyGoal(
                kcal = kcalValue ?: goal.kcal,
                proteinG = proteinValue ?: goal.proteinG,
                carbsG = carbsValue ?: goal.carbsG,
                fatG = fatValue ?: goal.fatG,
                targetWeightKg = targetWeight.normalizeDecimal().toDoubleOrNull(),
            )
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ziele einrichten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Zurück")
                    }
                },
                actions = {
                    TextButton(onClick = onDone) { Text("Überspringen") }
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
            Text(
                "Diese Werte kannst du jederzeit in den Einstellungen anpassen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Body weight — drives the protein/fat recommendations (not a goal itself).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Körpergewicht (kg)")
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
                            Text("Übernehmen")
                        }
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
            Text("Makros & Kalorien", style = MaterialTheme.typography.titleMedium)

            GoalField(
                label = "Protein (g)",
                value = protein,
                onValueChange = { protein = it },
                accentColor = ProteinColor,
                supportingText = "Empfehlung: 2,2g pro kg Körpergewicht",
            )
            GoalField(
                label = "Fett (g)",
                value = fat,
                onValueChange = { fat = it },
                accentColor = FatColor,
                supportingText = "Empfehlung: 1g pro kg Körpergewicht",
            )
            GoalField(
                label = "Kohlenhydrate (g)",
                value = carbs,
                onValueChange = { carbs = it },
                accentColor = CarbsColor,
            )
            if (calculatedCarbs != null) {
                TextButton(
                    onClick = { carbs = calculatedCarbs.toInt().toString() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Aus kcal & Makros berechnen (→ ${calculatedCarbs.toInt()}g)")
                }
            }
            GoalField(
                label = "Kalorien (kcal)",
                value = kcal,
                onValueChange = { kcal = it },
                accentColor = CalorieColor,
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
                                    "Kalorien stimmen nicht mit Makros überein",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                val sign = if (kcalDelta > 0) "+" else ""
                                Text(
                                    "Makros ergeben ${calculatedKcal.toInt()} kcal " +
                                        "(${sign}${kcalDelta.toInt()} kcal Unterschied)",
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
                label = "Zielgewicht (kg)",
                value = targetWeight,
                onValueChange = { targetWeight = it },
                decimal = true,
                suffix = "kg",
                supportingText = "Optional — wird in der Gewichtsstatistik als Linie angezeigt",
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { persistAndFinish() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Speichern & loslegen", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
