package dev.antonlammers.macrotrac.ui.workout

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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.ui.components.NumericTextField

/**
 * The live-session screen (spec §3.3, grundgerüst). Renders [WorkoutSessionViewModel]'s ui state:
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
    var showPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) navController.popBackStack()
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Workout verwerfen?") },
            text = { Text("Die laufende Einheit wird gelöscht und nicht gespeichert.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.discard()
                }) { Text("Verwerfen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Verwerfen",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = viewModel::finish, enabled = !state.loading) {
                        Text("Fertig", style = MaterialTheme.typography.labelLarge)
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.exercises.isEmpty()) {
                    item { EmptySessionHint() }
                }
                items(state.exercises, key = { it.id }) { exercise ->
                    val index = state.exercises.indexOfFirst { it.id == exercise.id }
                    ExerciseCard(
                        exercise = exercise,
                        onRemove = { viewModel.removeExercise(index) },
                        onAddSet = { viewModel.addSet(index) },
                        onRemoveSet = { setIndex -> viewModel.removeSet(index, setIndex) },
                        onMoveSetUp = { setIndex -> viewModel.moveSetUp(index, setIndex) },
                        onMoveSetDown = { setIndex -> viewModel.moveSetDown(index, setIndex) },
                        onWeightChange = { setIndex, w -> viewModel.setWeight(index, setIndex, w) },
                        onRepsChange = { setIndex, r -> viewModel.setReps(index, setIndex, r) },
                        onToggleCompleted = { setIndex -> viewModel.toggleSetCompleted(index, setIndex) },
                        onSetTypeChange = { setIndex, type -> viewModel.setSetType(index, setIndex, type) },
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
                            "Übung hinzufügen",
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
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onMoveSetUp: (Int) -> Unit,
    onMoveSetDown: (Int) -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onSetTypeChange: (Int, SetType) -> Unit,
) {
    val weightCaption = if (exercise.type == ExerciseType.BODYWEIGHT) "ZUSATZ" else "KG"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Übung entfernen", tint = MaterialTheme.colorScheme.outline)
            }
        }

        // Column captions
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("SATZ", Modifier.width(32.dp))
            Caption(weightCaption, Modifier.weight(1f))
            Caption("WDH", Modifier.weight(1f))
            // spacers for the check + menu columns
            Box(Modifier.width(88.dp))
        }

        exercise.sets.forEachIndexed { setIndex, set ->
            val hint = exercise.lastPerformance.getOrNull(setIndex)
            SetRow(
                setNumber = setIndex + 1,
                set = set,
                weightPlaceholder = hint?.let { weightToText(it.weightKg).ifEmpty { "0" } },
                repsPlaceholder = hint?.let { it.reps.takeIf { r -> r != 0 }?.toString() },
                onWeightChange = { onWeightChange(setIndex, it) },
                onRepsChange = { onRepsChange(setIndex, it) },
                onToggleCompleted = { onToggleCompleted(setIndex) },
                onSetTypeChange = { onSetTypeChange(setIndex, it) },
                onMoveUp = { onMoveSetUp(setIndex) },
                onMoveDown = { onMoveSetDown(setIndex) },
                onDelete = { onRemoveSet(setIndex) },
                canMoveUp = setIndex > 0,
                canMoveDown = setIndex < exercise.sets.lastIndex,
            )
        }

        TextButton(onClick = onAddSet) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Satz hinzufügen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    set: SetEntry,
    weightPlaceholder: String?,
    repsPlaceholder: String?,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    onToggleCompleted: () -> Unit,
    onSetTypeChange: (SetType) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    // Local text is the field's source of truth (so intermediate values like "82." survive); it is
    // seeded from the set and re-seeded only when the stable set id changes (never mid-edit/reorder).
    var weightText by remember(set.id) { mutableStateOf(weightToText(set.weightKg)) }
    var repsText by remember(set.id) { mutableStateOf(repsToText(set.reps)) }
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Satz erledigt", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = "Satz offen", tint = MaterialTheme.colorScheme.outline)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Satz-Optionen", tint = MaterialTheme.colorScheme.outline)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Nach oben") },
                    enabled = canMoveUp,
                    onClick = { menuOpen = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text("Nach unten") },
                    enabled = canMoveDown,
                    onClick = { menuOpen = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text("Löschen") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * The leading set marker: the set number tinted by its [SetType] (plus a short W/D/F tag for
 * non-normal types). Tapping opens a menu to change the type. Discreet, colour-token-only (spec §6).
 */
@Composable
private fun SetTypeBadge(
    setNumber: Int,
    type: SetType,
    onTypeChange: (SetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tint = type.color()
    Box(modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { menuOpen = true }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(setNumber.toString(), style = MaterialTheme.typography.labelMedium, color = tint)
            if (type != SetType.NORMAL) {
                Text(type.shortLabel(), style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SetType.selectable.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName(), color = option.color()) },
                    onClick = { menuOpen = false; onTypeChange(option) },
                )
            }
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
            "Noch keine Übungen — füge welche über den Button unten hinzu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun parseWeight(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0
private fun parseReps(text: String): Int = text.filter { it.isDigit() }.toIntOrNull() ?: 0
private fun weightToText(weightKg: Double): String =
    if (weightKg == 0.0) "" else if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else weightKg.toString()
private fun repsToText(reps: Int): String = if (reps == 0) "" else reps.toString()
