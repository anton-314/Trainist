package dev.antonlammers.trainist.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.antonlammers.trainist.domain.model.ThemeMode
import dev.antonlammers.trainist.ui.data.DataSheet
import dev.antonlammers.trainist.ui.data.DataViewModel
import dev.antonlammers.trainist.ui.data.toDisplayString
import dev.antonlammers.trainist.ui.goals.GoalsViewModel
import dev.antonlammers.trainist.ui.navigation.Screen
import dev.antonlammers.trainist.ui.theme.ThemeViewModel
import dev.antonlammers.trainist.ui.util.findActivity

/**
 * Settings hub — a short list of grouped rows, not a scroll of inline forms.
 *
 * Rows report their current value inline (the calorie goal, the picked language) so the common case
 * needs no tap at all. Tapping one opens a [SettingsSheet] over the hub; a pushed screen is reserved
 * for a real form (the goal editor) or long-form reading (the Help Center).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    goalsViewModel: GoalsViewModel = hiltViewModel(),
    dataViewModel: DataViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val dataState by dataViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDataSheet by remember { mutableStateOf(false) }

    // A finished export/import closes the sheet and reports here: a snackbar shown while the modal
    // sheet is up would render behind it.
    LaunchedEffect(dataState.message) {
        dataState.message?.let {
            showDataSheet = false
            snackbar.showSnackbar(it.toDisplayString(context))
            dataViewModel.clearMessage()
        }
    }

    if (showDataSheet) {
        DataSheet(viewModel = dataViewModel, onDismiss = { showDataSheet = false })
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SettingsGroupLabel(stringResource(R.string.settings_group_nutrition))
            GoalsRow(goalsViewModel, navController)

            SettingsGroupLabel(stringResource(R.string.settings_group_notifications))
            ReminderRow(dataViewModel)

            SettingsGroupLabel(stringResource(R.string.settings_group_app))
            SettingsGroup {
                ThemeRow(themeViewModel)
                SettingsRowDivider()
                LanguageRow(languageViewModel)
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Backup,
                    title = stringResource(R.string.settings_data_row_title),
                    onClick = { showDataSheet = true },
                    trailing = { Chevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Rounded.HelpOutline,
                    title = stringResource(R.string.settings_help_row_title),
                    supportingText = stringResource(R.string.settings_help_row_description),
                    onClick = { navController.navigate(Screen.Help.route) },
                    trailing = { Chevron() },
                )
            }

            // Also in the Help Center, but repeated here on purpose: a donation the user has to go
            // looking for is a donation that doesn't happen.
            DonateButton(modifier = Modifier.padding(top = 20.dp))

            VersionFooter()
            SettingsBottomSpacer()
        }
    }
}

/** Daily goals — the row previews the calorie target so the common case needs no tap. */
@Composable
private fun GoalsRow(viewModel: GoalsViewModel, navController: NavController) {
    val goal by viewModel.goal.collectAsStateWithLifecycle()

    SettingsGroup {
        SettingsRow(
            icon = Icons.Rounded.TrackChanges,
            title = stringResource(R.string.goals_section_header),
            value = stringResource(R.string.settings_goals_row_value, goal.kcal.toInt()),
            onClick = { navController.navigate(Screen.Goals.route) },
            trailing = { Chevron() },
        )
    }
}

/** Single switch — stays inline; a whole screen for one control would be pure indirection. */
@Composable
private fun ReminderRow(viewModel: DataViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsGroup {
        SettingsRow(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_reminder_title),
            supportingText = stringResource(R.string.settings_reminder_description),
            trailing = {
                Switch(
                    checked = state.reminderEnabled,
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
            },
        )
    }
}

/**
 * Language picker: a row showing the current selection, tapping opens a bottom sheet with
 * Systemsprache/Deutsch/English. [LanguageViewModel] delegates to `AppCompatDelegate`, which
 * recreates any *registered* `AppCompatActivity` automatically on API 33+; `MainActivity` is a plain
 * `ComponentActivity` (see CLAUDE.md's i18n bullet), so below API 33 the activity is recreated
 * explicitly here after a pick, mirroring `attachBaseContext`'s own `SDK_INT < TIRAMISU` condition.
 */
@Composable
private fun LanguageRow(viewModel: LanguageViewModel) {
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

    SettingsRow(
        icon = Icons.Rounded.Language,
        title = stringResource(R.string.settings_language_section_header),
        value = currentTag.languageDisplayName(),
        onClick = { showPicker = true },
        trailing = { Chevron() },
    )
}

/**
 * App appearance: light, dark, or whatever the system is set to. Picking applies immediately — the
 * whole app is composed inside a `TrainistTheme` reading the same repository-backed flow, so no
 * recreation is needed (unlike the language row below).
 */
@Composable
private fun ThemeRow(viewModel: ThemeViewModel) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        SettingsSheet(
            title = stringResource(R.string.settings_theme_picker_title),
            onDismiss = { showPicker = false },
        ) {
            ThemeMode.entries.forEach { option ->
                SettingsOptionRow(
                    label = option.displayName(),
                    selected = option == mode,
                    onSelect = {
                        viewModel.setThemeMode(option)
                        showPicker = false
                    },
                )
            }
        }
    }

    SettingsRow(
        icon = Icons.Rounded.DarkMode,
        title = stringResource(R.string.settings_theme_row_title),
        value = mode.displayName(),
        onClick = { showPicker = true },
        trailing = { Chevron() },
    )
}

@Composable
private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

/** Affordance marking a row that opens a sub-screen or a picker sheet. */
@Composable
private fun Chevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
    )
}

/** App version, shown at the very bottom of the settings hub (see [BuildConfig.VERSION_NAME]). */
@Composable
private fun ColumnScope.VersionFooter() {
    Text(
        stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 24.dp),
    )
}

@Composable
private fun String?.languageDisplayName(): String = when (this) {
    null -> stringResource(R.string.settings_language_system)
    "de" -> stringResource(R.string.settings_language_german)
    "en" -> stringResource(R.string.settings_language_english)
    "fr" -> stringResource(R.string.settings_language_french)
    "es" -> stringResource(R.string.settings_language_spanish)
    else -> this
}

@Composable
private fun LanguagePickerSheet(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsSheet(
        title = stringResource(R.string.settings_language_picker_title),
        onDismiss = onDismiss,
    ) {
        listOf(null, "de", "en", "fr", "es").forEach { tag ->
            SettingsOptionRow(
                label = tag.languageDisplayName(),
                selected = tag == selected,
                onSelect = { onSelect(tag) },
            )
        }
    }
}
