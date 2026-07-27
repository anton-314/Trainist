package dev.antonlammers.trainist.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.settings.DonateButton
import dev.antonlammers.trainist.ui.settings.FeedbackButton
import dev.antonlammers.trainist.ui.settings.SettingsBottomSpacer
import dev.antonlammers.trainist.ui.settings.SettingsGroup
import dev.antonlammers.trainist.ui.settings.SettingsGroupLabel
import dev.antonlammers.trainist.ui.settings.SettingsRow
import dev.antonlammers.trainist.ui.settings.SettingsRowDivider

/**
 * Help Center — getting started, FAQ by topic, glossary, and the way to reach the developer.
 *
 * A pushed screen rather than a [dev.antonlammers.trainist.ui.settings.SettingsSheet]: this is
 * long-form reading, and a sheet that has to be scrolled through dozens of entries fights the
 * gesture that dismisses it. It reuses the settings hub's row vocabulary (group label + bordered
 * card + hairline divider) so it reads as part of the same surface. Static content, no ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController, onStartTutorial: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_help_row_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )

            // The tour explains the same first steps by pointing at the real buttons — offered
            // above the written version, because being shown beats being told.
            SettingsGroupLabel(stringResource(R.string.help_section_tour))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(R.string.help_tutorial_row_title),
                    supportingText = stringResource(R.string.help_tutorial_row_subtitle),
                    onClick = onStartTutorial,
                )
            }

            // Getting started stays open: someone opening the help on day one shouldn't have to
            // guess which of four collapsed rows explains where to begin.
            SettingsGroupLabel(stringResource(R.string.help_section_start))
            SettingsGroup {
                HelpContent.gettingStarted.forEachIndexed { index, step ->
                    if (index > 0) SettingsRowDivider()
                    HelpStepRow(number = index + 1, step = step)
                }
            }

            HelpContent.sections.forEach { section ->
                SettingsGroupLabel(stringResource(section.title))
                SettingsGroup {
                    section.entries.forEachIndexed { index, entry ->
                        if (index > 0) SettingsRowDivider()
                        HelpExpandableRow(entry)
                    }
                }
            }

            ContactSection()
            SettingsBottomSpacer()
        }
    }
}

/** A numbered getting-started step — always expanded, so the intro reads as one short guide. */
@Composable
private fun HelpStepRow(number: Int, step: HelpEntry) {
    Row(modifier = Modifier.padding(16.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(step.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One question or glossary term. Collapsed by default — the value of an FAQ is being able to scan
 * the questions; `rememberSaveable` keeps what the user opened across rotation.
 */
@Composable
private fun HelpExpandableRow(entry: HelpEntry) {
    var expanded by rememberSaveable(entry.title) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "helpChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(entry.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(expanded) {
            Text(
                stringResource(entry.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The end of the help is the way out of it: a question the FAQ doesn't answer goes straight to the
 * developer. The donation ask sits below it — the same pair the (now removed) support sheet held.
 */
@Composable
private fun ColumnScope.ContactSection() {
    SettingsGroupLabel(stringResource(R.string.help_section_contact))
    Text(
        stringResource(R.string.help_contact_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    FeedbackButton()

    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.settings_donation_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.settings_donation_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    DonateButton()
}
