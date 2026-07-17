package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.ExerciseSessionLog
import dev.antonlammers.trainist.domain.PerformedSet
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.ui.stats.StrengthChart
import dev.antonlammers.trainist.ui.stats.evenlySpacedDates
import dev.antonlammers.trainist.ui.util.localizedDateFormatter
import java.time.LocalDate

/**
 * Per-exercise detail: the exercise's catalog metadata, its current max-weight PR, an
 * all-time strength (estimated-1RM) mini-chart (reusing the stats `StrengthChart`), and the complete
 * chronological set log across all sessions. Reached from the catalog, the live session and the
 * history. Ink & Paper style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    navController: NavController,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: stringResource(R.string.exercise_detail_title_fallback), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.history.personalRecordKg?.let { pr ->
                    item(key = "pr") { PrCard(pr) }
                }

                item(key = "strength") {
                    DetailCard(stringResource(R.string.exercise_detail_strength_header)) {
                        val strength = state.strength
                        if (strength.samples.size < 2) {
                            Text(
                                stringResource(R.string.exercise_detail_strength_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val spanDays = strength.rangeEnd.toEpochDay() - strength.rangeStart.toEpochDay()
                            val fmt = if (spanDays >= 180) monthYearFmt() else dayMonthFmt()
                            StrengthChart(
                                data = strength,
                                tickDates = evenlySpacedDates(strength.rangeStart, strength.rangeEnd, 4),
                                tickFormatter = fmt,
                                lineColor = MaterialTheme.colorScheme.primary,
                                gridColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                            )
                        }
                    }
                }

                state.exercise?.let { exercise ->
                    if (exercise.hasMetadata) {
                        item(key = "meta") { MetadataCard(exercise) }
                    }
                }

                item(key = "history-header") {
                    Text(stringResource(R.string.exercise_detail_history_header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (state.history.sessions.isEmpty()) {
                    item(key = "history-empty") {
                        Text(
                            stringResource(R.string.exercise_detail_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.history.sessions, key = { it.sessionId }) { session ->
                        SessionLogCard(session, type = state.exercise?.type ?: ExerciseType.WEIGHT_REPS)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrCard(prKg: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.exercise_detail_pr_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatKg(prKg)} kg", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun MetadataCard(exercise: Exercise) {
    DetailCard(stringResource(R.string.exercise_detail_metadata_header)) {
        MetaField(stringResource(R.string.exercise_editor_type_label), exercise.type.displayName())
        exercise.primaryMuscles.takeIf { it.isNotEmpty() }?.let {
            MetaField(stringResource(R.string.exercise_detail_muscles_label), it.joinToString(", ") { m -> m.titleCase() })
        }
        exercise.equipment?.let { MetaField(stringResource(R.string.exercise_catalog_equipment_filter_label), it.titleCase()) }
        exercise.mechanic?.let { MetaField(stringResource(R.string.exercise_editor_mechanic_label), it.displayName()) }
        exercise.category?.let { MetaField(stringResource(R.string.exercise_detail_category_label), it.titleCase()) }
        if (exercise.instructions.isNotEmpty()) {
            Text(stringResource(R.string.exercise_detail_instructions_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            exercise.instructions.forEachIndexed { index, line ->
                Text("${index + 1}. $line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MetaField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionLogCard(session: ExerciseSessionLog, type: ExerciseType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(session.date.format(sessionDateFmt()), style = MaterialTheme.typography.titleMedium)
        session.sets.forEach { set -> SetLogRow(set, type) }
    }
}

@Composable
private fun SetLogRow(set: PerformedSet, type: ExerciseType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            set.setNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = set.type.color(),
            modifier = Modifier.size(width = 20.dp, height = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(setLine(type, set), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (set.type != SetType.NORMAL) {
            Text(set.type.displayName(), style = MaterialTheme.typography.labelSmall, color = set.type.color())
        }
        if (set.isPersonalRecord) PrBadge()
    }
}

/** Human line for a logged set: "80 kg × 5", "KG +10 × 8", or "KG × 8" (bodyweight, no added). */
@Composable
private fun setLine(type: ExerciseType, set: PerformedSet): String {
    val kgCaption = stringResource(R.string.workout_session_weight_caption_kg)
    val weightText = when {
        type == ExerciseType.BODYWEIGHT && set.weightKg == 0.0 -> kgCaption
        type == ExerciseType.BODYWEIGHT -> "$kgCaption +${formatKg(set.weightKg)}"
        else -> "${formatKg(set.weightKg)} kg"
    }
    return "$weightText × ${set.reps}"
}

/** Flat hairline card with a mono caption header, matching the Ink & Paper cards. */
@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

private val Exercise.hasMetadata: Boolean
    get() = primaryMuscles.isNotEmpty() || equipment != null || mechanic != null || category != null || instructions.isNotEmpty()

private fun sessionDateFmt() = localizedDateFormatter("d. MMMM yyyy")
private fun dayMonthFmt() = localizedDateFormatter("d.M.")
private fun monthYearFmt() = localizedDateFormatter("MMM yy")
