package dev.antonlammers.trainist.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.BuildConfig
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.MacroCalculator
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.data.DataViewModel
import dev.antonlammers.trainist.ui.data.toDisplayString
import dev.antonlammers.trainist.ui.goals.FieldLabel
import dev.antonlammers.trainist.ui.goals.GoalField
import dev.antonlammers.trainist.ui.goals.formatWeight
import dev.antonlammers.trainist.ui.goals.GoalsViewModel
import dev.antonlammers.trainist.ui.theme.CalorieColor
import dev.antonlammers.trainist.ui.theme.CarbsColor
import dev.antonlammers.trainist.ui.theme.FatColor
import dev.antonlammers.trainist.ui.theme.ProteinColor
import dev.antonlammers.trainist.ui.util.findActivity
import dev.antonlammers.trainist.util.normalizeDecimal
import kotlinx.coroutines.launch

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
    languageViewModel: LanguageViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
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
            SectionHeader(stringResource(R.string.settings_data_section_header))
            DataSection(dataViewModel, snackbar)

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_language_section_header))
            LanguageSection(languageViewModel)

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_support_section_header))
            DonationSection()

            VersionFooter()

            // Extra bottom breathing room so the last card clears the navigation bar.
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** App version, shown at the very bottom of the settings hub (see [BuildConfig.VERSION_NAME]). */
@Composable
private fun ColumnScope.VersionFooter() {
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
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

    SectionHeader(stringResource(R.string.goals_section_header))

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

    // Resolved here (not inside the onClick lambda below, which isn't a @Composable context).
    val goalsSavedMessage = stringResource(R.string.goals_saved_message)

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
            scope.launch { snackbar.showSnackbar(goalsSavedMessage) }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelLarge)
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
            snackbar.showSnackbar(it.toDisplayString(context))
            viewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it) }
    }

    // Resolved here (not inside the onClick lambda below, which isn't a @Composable context).
    val exportChooserTitle = stringResource(R.string.settings_export_chooser_title)

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
                context.startActivity(Intent.createChooser(intent, exportChooserTitle))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !dataState.isLoading,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.settings_export_button))
    }

    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/zip", "text/csv", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !dataState.isLoading,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.settings_import_button))
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
                Text(stringResource(R.string.settings_reminder_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_reminder_description),
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

/**
 * Language picker: a flat card showing the current selection, tapping opens a bottom sheet with
 * Systemsprache/Deutsch/English. [LanguageViewModel] delegates to
 * `AppCompatDelegate`, which recreates any *registered* `AppCompatActivity` automatically on API
 * 33+; `MainActivity` is a plain `ComponentActivity` (see CLAUDE.md's i18n bullet), so below API 33
 * the activity is recreated explicitly here after a pick, mirroring `attachBaseContext`'s own
 * `SDK_INT < TIRAMISU` condition.
 */
@Composable
private fun ColumnScope.LanguageSection(viewModel: LanguageViewModel) {
    val currentTag by viewModel.language.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showPicker) {
        LanguagePickerSheet(
            selected = currentTag,
            onSelect = { tag ->
                viewModel.setLanguage(tag)
                showPicker = false
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    context.findActivity()?.recreate()
                }
            },
            onDismiss = { showPicker = false },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_language_section_header), style = MaterialTheme.typography.titleMedium)
            Text(
                currentTag.languageDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun String?.languageDisplayName(): String = when (this) {
    null -> stringResource(R.string.settings_language_system)
    "de" -> stringResource(R.string.settings_language_german)
    "en" -> stringResource(R.string.settings_language_english)
    else -> this
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.settings_language_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LanguageOptionRow(null, selected, stringResource(R.string.settings_language_system), onSelect)
            LanguageOptionRow("de", selected, stringResource(R.string.settings_language_german), onSelect)
            LanguageOptionRow("en", selected, stringResource(R.string.settings_language_english), onSelect)
        }
    }
}

@Composable
private fun LanguageOptionRow(tag: String?, selected: String?, label: String, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(tag) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A flat card linking out to the developer's PayPal.me page for optional donations. */
@Composable
private fun ColumnScope.DonationSection() {
    val context = LocalContext.current
    // Resolved here (not inside the onClick lambda below, which isn't a @Composable context).
    val emailSubject = stringResource(R.string.settings_contact_email_subject)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_donation_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_donation_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/antonlamm"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.Rounded.VolunteerActivism,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.settings_donation_cta))
            }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_CONTACT_EMAIL")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.Rounded.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.settings_contact_developer_button))
            }
        }
    }
}

/** Support inbox for feedback, bugs and feature suggestions (see [DonationSection]). */
private const val DEVELOPER_CONTACT_EMAIL = "lammy.google.develop.flatness494@passmail.net"

/** Mono-uppercase section header (matches the Stats "Daten" caption). */
@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
