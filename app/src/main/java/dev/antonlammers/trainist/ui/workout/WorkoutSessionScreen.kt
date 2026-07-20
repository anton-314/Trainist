package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.notification.RestTimerService
import dev.antonlammers.trainist.ui.components.DragReorderColumn
import dev.antonlammers.trainist.ui.components.NumericTextField
import dev.antonlammers.trainist.ui.navigation.Screen
import dev.antonlammers.trainist.ui.util.currentAppLocale

/**
 * The live-session screen. Renders [WorkoutSessionViewModel]'s ui state:
 * a list of exercises, each with editable set rows (weight, reps, check-off, reorder, delete) and an
 * "add set" action, plus an "add exercise" catalog picker. Leaving via back keeps the session running
 * (it is persisted continuously); the top-bar "Fertig" finishes it, the delete icon discards it.
 * Ink & Paper style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    navController: NavController,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val restTimer by viewModel.restTimer.collectAsStateWithLifecycle()
    val pendingTemplateUpdate by viewModel.pendingTemplateUpdate.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) navController.popBackStack()
    }

    // The VM stays Android-free: it emits the timer state, mirrored here into RestTimerService, which
    // owns the countdown for its whole duration and fires the alert itself.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.restCommands.collect { command ->
            when (command) {
                is RestCommand.Sync -> RestTimerService.sync(
                    context = context,
                    exerciseName = command.exerciseName,
                    totalSeconds = command.totalSeconds,
                    endAtMs = command.endAtMs,
                    pausedRemainingMs = command.pausedRemainingMs,
                )
                RestCommand.Stop -> RestTimerService.stop(context)
            }
        }
    }

    if (showPicker) {
        val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
        val pickerResults by viewModel.pickerResults.collectAsStateWithLifecycle()
        ExercisePickerSheet(
            query = pickerQuery,
            results = pickerResults,
            onQueryChange = viewModel::onPickerQueryChange,
            onDismiss = { showPicker = false },
            onPick = { exercise ->
                viewModel.addExercise(exercise)
                showPicker = false
            },
        )
    }

    pendingTemplateUpdate?.let { merged ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTemplateUpdate,
            title = { Text(stringResource(R.string.workout_session_template_update_title)) },
            text = {
                Text(stringResource(R.string.workout_session_template_update_text, merged.name))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmTemplateUpdate) { Text(stringResource(R.string.workout_session_template_update_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTemplateUpdate) { Text(stringResource(R.string.workout_session_template_update_dismiss)) }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.workout_session_discard_title)) },
            text = { Text(stringResource(R.string.workout_session_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.workout_session_discard_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_session_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.workout_session_discard_confirm),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = viewModel::finish, enabled = !state.loading) {
                        Text(stringResource(R.string.workout_session_finish_button), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        bottomBar = {
            restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    onPauseResume = { if (timer.isPaused) viewModel.resumeRest() else viewModel.pauseRest() },
                    onAdjust = viewModel::adjustRest,
                    onSkip = viewModel::skipRest,
                )
            }
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.exercises.isEmpty()) {
                    item { EmptySessionHint() }
                }
                items(state.exercises, key = { it.id }) { exercise ->
                    val index = state.exercises.indexOfFirst { it.id == exercise.id }
                    ExerciseCard(
                        exercise = exercise,
                        onOpenDetail = { navController.navigate(Screen.ExerciseDetail.forExercise(exercise.exerciseStableId)) },
                        onRemove = { viewModel.removeExercise(index) },
                        onAddSet = { viewModel.addSet(index) },
                        onRemoveSet = { setIndex -> viewModel.removeSet(index, setIndex) },
                        onMoveSet = { from, to -> viewModel.moveSet(index, from, to) },
                        onWeightChange = { setIndex, w -> viewModel.setWeight(index, setIndex, w) },
                        onRepsChange = { setIndex, r -> viewModel.setReps(index, setIndex, r) },
                        onToggleCompleted = { setIndex -> viewModel.toggleSetCompleted(index, setIndex) },
                        onSetTypeChange = { setIndex, type -> viewModel.setSetType(index, setIndex, type) },
                        onRestChange = { seconds -> viewModel.setExerciseRest(exercise.exerciseStableId, seconds) },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.workout_add_exercise_button),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: SessionExerciseUi,
    onOpenDetail: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onMoveSet: (from: Int, to: Int) -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onSetTypeChange: (Int, SetType) -> Unit,
    onRestChange: (Int) -> Unit,
) {
    val weightCaption = stringResource(
        if (exercise.type == ExerciseType.BODYWEIGHT) {
            R.string.workout_session_weight_caption_added
        } else {
            R.string.workout_session_weight_caption_kg
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenDetail),
            )
            RestDurationChip(restSeconds = exercise.restSeconds, onRestChange = onRestChange)
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.workout_session_remove_exercise_content_description), tint = MaterialTheme.colorScheme.outline)
            }
        }

        // Column captions — spacing must mirror SetRow's spacedBy(8.dp) so captions sit above their fields.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(28.dp)) // spacer for the drag-handle column
            Caption(stringResource(R.string.workout_session_set_number_caption), Modifier.width(32.dp))
            Caption(weightCaption, Modifier.weight(1f))
            Caption(stringResource(R.string.workout_session_reps_caption), Modifier.weight(1f))
            // spacers for the check + delete columns
            Box(Modifier.width(88.dp))
        }

        DragReorderColumn(
            items = exercise.sets,
            key = { it.id },
            onMove = onMoveSet,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { setIndex, set, rowModifier, dragHandleModifier, isDragging ->
            val hint = exercise.lastPerformance.getOrNull(setIndex)
            SetRow(
                setNumber = setIndex + 1,
                set = set,
                weightPlaceholder = hint?.let { weightToText(it.weightKg).ifEmpty { "0" } },
                repsPlaceholder = hint?.let { it.reps.takeIf { r -> r != 0 }?.toString() },
                isDragging = isDragging,
                rowModifier = rowModifier,
                dragHandleModifier = dragHandleModifier,
                onWeightChange = { onWeightChange(setIndex, it) },
                onRepsChange = { onRepsChange(setIndex, it) },
                onToggleCompleted = { onToggleCompleted(setIndex) },
                onSetTypeChange = { onSetTypeChange(setIndex, it) },
                onDelete = { onRemoveSet(setIndex) },
            )
        }

        TextButton(onClick = onAddSet) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.workout_add_set_button), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }

        // Volume + estimated 1RM summary — only once at least one work set has been logged.
        if (exercise.volumeKg > 0.0 || exercise.estimatedOneRepMaxKg != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                MetricStat(stringResource(R.string.workout_session_volume_label), "${formatKg(exercise.volumeKg)} kg")
                exercise.estimatedOneRepMaxKg?.let { MetricStat("E1RM", "${formatKg(it)} kg") }
            }
        }
    }
}

/** A compact mono-caption + serif-value stat pair, matching the Ink & Paper summary language. */
@Composable
private fun MetricStat(label: String, value: String) {
    Column {
        Caption(label)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    set: SetEntry,
    weightPlaceholder: String?,
    repsPlaceholder: String?,
    isDragging: Boolean,
    rowModifier: Modifier,
    dragHandleModifier: Modifier,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    onToggleCompleted: () -> Unit,
    onSetTypeChange: (SetType) -> Unit,
    onDelete: () -> Unit,
) {
    // Local text is the field's source of truth (so intermediate values like "82." survive); it is
    // seeded from the set and re-seeded when the stable set id changes (never mid-edit/reorder) or when
    // the set gets checked off — so the values adopted from the inline-history placeholder on check-off
    // (see WorkoutSessionViewModel.toggleSetCompleted) show up as real text instead of the grey hint.
    var weightText by remember(set.id, set.completed) { mutableStateOf(weightToText(set.weightKg)) }
    var repsText by remember(set.id, set.completed) { mutableStateOf(repsToText(set.reps)) }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = stringResource(R.string.workout_session_drag_handle_content_description),
            tint = MaterialTheme.colorScheme.outline,
            modifier = dragHandleModifier.size(28.dp),
        )
        SetTypeBadge(
            setNumber = setNumber,
            type = set.type,
            onTypeChange = onSetTypeChange,
            modifier = Modifier.width(32.dp),
        )
        NumericTextField(
            value = weightText,
            onValueChange = { weightText = it; onWeightChange(parseWeight(it)) },
            label = null,
            decimal = true,
            placeholder = weightPlaceholder,
            modifier = Modifier.weight(1f),
        )
        NumericTextField(
            value = repsText,
            onValueChange = { repsText = it; onRepsChange(parseReps(it)) },
            label = null,
            decimal = false,
            placeholder = repsPlaceholder,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleCompleted, modifier = Modifier.size(40.dp)) {
            if (set.completed) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.workout_session_set_completed_content_description), tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = stringResource(R.string.workout_session_set_open_content_description), tint = MaterialTheme.colorScheme.outline)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.workout_session_delete_set_content_description), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun EmptySessionHint() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.workout_session_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rest timer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The sticky rest-timer bar: a progress line over the remaining mm:ss, with ±15 s adjust, a
 * pause/resume toggle and skip. Flat, hairline-topped, Ink & Paper.
 */
@Composable
private fun RestTimerBar(
    timer: RestTimerUiState,
    onPauseResume: () -> Unit,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val fraction = if (timer.totalSeconds > 0) {
        (timer.remainingSeconds.toFloat() / timer.totalSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(if (timer.isPaused) R.string.workout_session_rest_paused_label else R.string.workout_session_rest_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatMmSs(timer.remainingSeconds), style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { onAdjust(-ADJUST_STEP_SECONDS) }) { Text("−15") }
                IconButton(onClick = onPauseResume) {
                    if (timer.isPaused) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.workout_session_rest_resume_content_description), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.workout_session_rest_pause_content_description), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = { onAdjust(ADJUST_STEP_SECONDS) }) { Text("+15") }
                IconButton(onClick = onSkip) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.workout_session_rest_skip_content_description), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Per-exercise rest override: a hairline pill showing the current duration; tap picks a preset. */
@Composable
private fun RestDurationChip(restSeconds: Int, onRestChange: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .clickable { menuOpen = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "${restSeconds}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            REST_PRESETS.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text("${seconds}s") },
                    onClick = { menuOpen = false; onRestChange(seconds) },
                )
            }
        }
    }
}

private const val ADJUST_STEP_SECONDS = 15
private val REST_PRESETS = listOf(30, 60, 90, 120, 150, 180, 240)

private fun formatMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(currentAppLocale(), safe / 60, safe % 60)
}

