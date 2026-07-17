package dev.antonlammers.trainist.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.ui.components.DragReorderColumn

/**
 * Full-screen editor for creating or editing a [dev.antonlammers.trainist.domain.model.WorkoutTemplate]:
 * a name, an ordered list of exercises (drag-to-reorder via [DragReorderColumn], remove) each with its
 * planned sets — one chip per set carrying a [SetTypeBadge] (tap to pick warmup/normal/drop/failure)
 * plus a remove ✕, and a "Satz hinzufügen" action — and an "add exercise" action that opens a
 * searchable catalog picker sheet. All editing logic lives in [TemplateEditorViewModel]; this screen
 * only renders it. Ink & Paper style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    navController: NavController,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) navController.popBackStack()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNewTemplate) R.string.template_editor_title_new else R.string.template_editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.canSave) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.common_save),
                            tint = if (state.canSave) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.template_editor_name_label)) },
                    placeholder = { Text(stringResource(R.string.template_editor_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            item {
                Text(
                    stringResource(R.string.template_editor_exercises_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.slots.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.template_editor_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    DragReorderColumn(
                        items = state.slots,
                        key = { it.id },
                        onMove = viewModel::moveSlot,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { index, slot, rowModifier, dragHandleModifier, isDragging ->
                        SlotCard(
                            slot = slot,
                            isDragging = isDragging,
                            rowModifier = rowModifier,
                            dragHandleModifier = dragHandleModifier,
                            onRemove = { viewModel.removeExercise(index) },
                            onAddSet = { viewModel.addSet(index) },
                            onRemoveSet = { setIndex -> viewModel.removeSet(index, setIndex) },
                            onSetTypeChange = { setIndex, type -> viewModel.setSetType(index, setIndex, type) },
                        )
                    }
                }
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

/** A single exercise slot: header (drag handle, name, remove) + its planned per-set-type list. */
@Composable
private fun SlotCard(
    slot: TemplateSlot,
    isDragging: Boolean,
    rowModifier: Modifier,
    dragHandleModifier: Modifier,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onSetTypeChange: (Int, SetType) -> Unit,
) {
    Column(
        modifier = rowModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = stringResource(R.string.workout_session_drag_handle_content_description),
                tint = MaterialTheme.colorScheme.outline,
                modifier = dragHandleModifier.size(28.dp),
            )

            Text(
                slot.exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.workout_session_remove_exercise_content_description),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 36.dp, top = 4.dp),
        ) {
            itemsIndexed(slot.setTypes) { setIndex, type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetTypeBadge(
                        setNumber = setIndex + 1,
                        type = type,
                        onTypeChange = { onSetTypeChange(setIndex, it) },
                        modifier = Modifier.width(36.dp),
                    )
                    IconButton(
                        onClick = { onRemoveSet(setIndex) },
                        enabled = slot.setTypes.size > 1,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.template_editor_remove_set_content_description),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            item {
                TextButton(onClick = onAddSet, enabled = slot.setTypes.size < 20) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.template_editor_add_set_short_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
