package dev.antonlammers.macrotrac.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.MacroCalculator
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import dev.antonlammers.macrotrac.ui.data.DataViewModel
import dev.antonlammers.macrotrac.ui.goals.GoalsViewModel
import dev.antonlammers.macrotrac.ui.theme.CalorieColor
import dev.antonlammers.macrotrac.ui.theme.CarbsColor
import dev.antonlammers.macrotrac.ui.theme.FatColor
import dev.antonlammers.macrotrac.ui.theme.ProteinColor
import dev.antonlammers.macrotrac.util.normalizeDecimal
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings hub: the daily-goals editor and the data section (backup export/import + reminder
 * toggle) in one place. Reachable from the bottom nav; replaces the former standalone Goals tab and
 * the "Daten" section that used to sit on the Stats screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    goalsViewModel: GoalsViewModel = hiltViewModel(),
    dataViewModel: DataViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
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
            GoalsSection(goalsViewModel, snackbar)

            HorizontalDivider()
            SectionHeader("Daten")
            DataSection(dataViewModel, snackbar)

            // Extra bottom breathing room so the last card clears the navigation bar.
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Daily-goals editor: body-weight-driven recommendations + macro/kcal fields + target weight. */
@Composable
private fun ColumnScope.GoalsSection(
    viewModel: GoalsViewModel,
    snackbar: SnackbarHostState,
) {
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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

    SectionHeader("Tagesziele")

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
        onClick = {
            viewModel.save(
                DailyGoal(
                    kcal = kcal.normalizeDecimal().toDoubleOrNull() ?: goal.kcal,
                    proteinG = protein.normalizeDecimal().toDoubleOrNull() ?: goal.proteinG,
                    carbsG = carbs.normalizeDecimal().toDoubleOrNull() ?: goal.carbsG,
                    fatG = fat.normalizeDecimal().toDoubleOrNull() ?: goal.fatG,
                    // Blank clears the target (null).
                    targetWeightKg = targetWeight.normalizeDecimal().toDoubleOrNull(),
                )
            )
            scope.launch { snackbar.showSnackbar("Ziele gespeichert") }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Speichern", style = MaterialTheme.typography.labelLarge)
    }
}

/** Backup export/import buttons + the daily-reminder toggle. */
@Composable
private fun ColumnScope.DataSection(
    viewModel: DataViewModel,
    snackbar: SnackbarHostState,
) {
    val dataState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it) }
    }

    if (dataState.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Button(
        onClick = {
            viewModel.export { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Exportieren via"))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !dataState.isLoading,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Vollständiges Backup exportieren")
    }

    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !dataState.isLoading,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Backup importieren")
    }

    // Daily meal reminder toggle.
    Card(
        modifier = Modifier.fillMaxWidth(),
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
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tägliche Erinnerung", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wenn du bis 17 Uhr noch keine Mahlzeit eingetragen hast, " +
                        "schickt dir die App eine kurze Erinnerung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = dataState.reminderEnabled,
                onCheckedChange = viewModel::setReminderEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/** Mono-uppercase section header (matches the Stats "Daten" caption). */
@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Mono-uppercase static caption shown above an input field. */
@Composable
private fun FieldLabel(label: String) {
    Text(
        label.uppercase(Locale("de")),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A goal input: a mono-uppercase caption above a serif-valued [NumericTextField], with an optional
 * 8×20dp colored accent tick (the macro's data color) in the leading slot.
 */
@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    decimal: Boolean = false,
    suffix: String? = null,
    supportingText: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        NumericTextField(
            value = value,
            onValueChange = onValueChange,
            label = null,
            decimal = decimal,
            suffix = suffix,
            supportingText = supportingText,
            textStyle = MaterialTheme.typography.titleMedium,
            leadingIcon = accentColor?.let { color -> { AccentTick(color) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Small vertical accent bar carrying a macro's data color. */
@Composable
private fun AccentTick(color: Color) {
    Box(
        Modifier
            .size(width = 8.dp, height = 20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/** Whole numbers without a decimal point, fractional weights as-is (e.g. 72 / 72.5). */
private fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
