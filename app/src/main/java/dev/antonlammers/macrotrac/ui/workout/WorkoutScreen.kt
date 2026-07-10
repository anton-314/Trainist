package dev.antonlammers.macrotrac.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import dev.antonlammers.macrotrac.ui.components.NumericTextField
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * "Training" tab — the searchable / filterable exercise catalog (bundled entries + custom ones).
 * Ink & Paper style, reusing the existing list/bottom-sheet building blocks. Catalog exercises are
 * read-only; custom exercises are created via the FAB and edited/deleted via swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showCreateSheet by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Exercise?>(null) }
    var exerciseDetail by remember { mutableStateOf<Exercise?>(null) }

    if (showCreateSheet) {
        ExerciseEditorSheet(
            onDismiss = { showCreateSheet = false },
            onSave = { draft ->
                viewModel.saveCustomExercise(
                    stableId = null,
                    name = draft.name,
                    type = draft.type,
                    primaryMuscles = draft.primaryMuscles,
                    equipment = draft.equipment,
                    mechanic = draft.mechanic,
                    instructions = draft.instructions,
                )
                showCreateSheet = false
            },
        )
    }

    exerciseToEdit?.let { exercise ->
        ExerciseEditorSheet(
            initial = exercise,
            onDismiss = { exerciseToEdit = null },
            onSave = { draft ->
                viewModel.saveCustomExercise(
                    stableId = exercise.stableId,
                    name = draft.name,
                    type = draft.type,
                    primaryMuscles = draft.primaryMuscles,
                    equipment = draft.equipment,
                    mechanic = draft.mechanic,
                    instructions = draft.instructions,
                )
                exerciseToEdit = null
            },
        )
    }

    exerciseDetail?.let { exercise ->
        ExerciseDetailSheet(exercise = exercise, onDismiss = { exerciseDetail = null })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Training") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Eigene Übung anlegen")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(query = state.query, onQueryChange = viewModel::onQueryChange)

            if (filterOptions.muscles.isNotEmpty()) {
                FilterChipRow(
                    label = "MUSKEL",
                    options = filterOptions.muscles,
                    selected = state.muscle,
                    onSelected = viewModel::onMuscleSelected,
                )
            }
            if (filterOptions.equipment.isNotEmpty()) {
                FilterChipRow(
                    label = "EQUIPMENT",
                    options = filterOptions.equipment,
                    selected = state.equipment,
                    onSelected = viewModel::onEquipmentSelected,
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (exercises.isEmpty()) {
                    item { EmptyHint("Keine Übungen gefunden.") }
                } else {
                    items(exercises, key = { it.stableId }) { exercise ->
                        if (exercise.isCustom) {
                            SwipeableCustomExerciseRow(
                                exercise = exercise,
                                onClick = { exerciseDetail = exercise },
                                onEdit = { exerciseToEdit = exercise },
                                onDelete = {
                                    viewModel.deletePending(exercise)
                                    coroutineScope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = "Übung gelöscht",
                                            actionLabel = "Rückgängig",
                                            duration = SnackbarDuration.Short,
                                        )
                                        when (result) {
                                            SnackbarResult.ActionPerformed -> viewModel.undoDelete(exercise)
                                            SnackbarResult.Dismissed -> viewModel.confirmDelete(exercise)
                                        }
                                    }
                                },
                            )
                        } else {
                            ExerciseRowContent(exercise = exercise, onClick = { exerciseDetail = exercise })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search + filter chrome
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Übung suchen") },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(option.titleCase(), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCustomExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        backgroundContent = { SwipeBackground(dismissState.targetValue) },
    ) {
        ExerciseRowContent(exercise = exercise, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue) {
    val bgColor = when (target) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 16.dp),
        contentAlignment = when (target) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            else -> Alignment.CenterEnd
        },
    ) {
        when (target) {
            SwipeToDismissBoxValue.EndToStart -> Icon(
                Icons.Rounded.Delete,
                contentDescription = "Löschen",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            SwipeToDismissBoxValue.StartToEnd -> Icon(
                Icons.Rounded.Edit,
                contentDescription = "Bearbeiten",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> {}
        }
    }
}

/** An exercise list row: name (sans) with a mono detail line (muscles · equipment) beneath. */
@Composable
private fun ExerciseRowContent(exercise: Exercise, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (!exercise.isCustom) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = "Katalog-Übung (schreibgeschützt)",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            exercise.detailLine(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Read-only detail sheet (catalog + custom)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(exercise: Exercise, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge)

            DetailField("TYP", exercise.type.displayName())
            exercise.primaryMuscles.takeIf { it.isNotEmpty() }?.let {
                DetailField("MUSKELN", it.joinToString(", ") { m -> m.titleCase() })
            }
            exercise.equipment?.let { DetailField("EQUIPMENT", it.titleCase()) }
            exercise.mechanic?.let { DetailField("MECHANIK", it.displayName()) }
            exercise.category?.let { DetailField("KATEGORIE", it.titleCase()) }

            if (exercise.instructions.isNotEmpty()) {
                Text(
                    "ANLEITUNG",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.instructions.forEachIndexed { index, line ->
                    Text(
                        "${index + 1}. $line",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom-exercise editor sheet (create / edit) — CustomFoodDialog pattern
// ─────────────────────────────────────────────────────────────────────────────

/** Raw editor result; the ViewModel owns id minting and persistence. */
data class ExerciseDraft(
    val name: String,
    val type: ExerciseType,
    val primaryMuscles: List<String>,
    val equipment: String?,
    val mechanic: Mechanic?,
    val instructions: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditorSheet(
    initial: Exercise? = null,
    onDismiss: () -> Unit,
    onSave: (ExerciseDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ExerciseType.WEIGHT_REPS) }
    var muscles by remember { mutableStateOf(initial?.primaryMuscles?.joinToString(", ") ?: "") }
    var equipment by remember { mutableStateOf(initial?.equipment ?: "") }
    var mechanic by remember { mutableStateOf(initial?.mechanic) }
    var instructions by remember { mutableStateOf(initial?.instructions?.joinToString("\n") ?: "") }

    val isValid = name.isNotBlank()

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial == null) "Neue Übung" else "Übung bearbeiten",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("TYP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.entries.forEach { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = { type = t },
                        label = { Text(t.displayName(), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            OutlinedTextField(
                muscles, { muscles = it },
                label = { Text("Muskeln (Komma-getrennt)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipment") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("MECHANIK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mechanic.entries.forEach { m ->
                    FilterChip(
                        selected = m == mechanic,
                        onClick = { mechanic = if (mechanic == m) null else m },
                        label = { Text(m.displayName(), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            OutlinedTextField(
                instructions, { instructions = it },
                label = { Text("Anleitung (eine Zeile pro Schritt)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(
                        ExerciseDraft(
                            name = name.trim(),
                            type = type,
                            primaryMuscles = muscles.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            equipment = equipment.trim().takeIf { it.isNotEmpty() },
                            mechanic = mechanic,
                            instructions = instructions.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Speichern", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Display helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Exercise.detailLine(): String {
    val muscles = primaryMuscles.joinToString(", ") { it.titleCase() }
    return listOfNotNull(
        muscles.takeIf { it.isNotEmpty() },
        equipment?.titleCase(),
    ).joinToString(" · ").ifEmpty { type.displayName() }
}

private fun String.titleCase(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}

private fun ExerciseType.displayName(): String = when (this) {
    ExerciseType.WEIGHT_REPS -> "Gewicht × Reps"
    ExerciseType.BODYWEIGHT -> "Körpergewicht"
}

private fun Mechanic.displayName(): String = when (this) {
    Mechanic.COMPOUND -> "Verbund"
    Mechanic.ISOLATION -> "Isolation"
}
