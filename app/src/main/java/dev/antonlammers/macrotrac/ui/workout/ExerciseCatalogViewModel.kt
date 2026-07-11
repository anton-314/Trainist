package dev.antonlammers.macrotrac.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The selectable filter options, derived from the current catalog contents. */
data class ExerciseFilterOptions(
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
)

data class WorkoutUiState(
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
)

@HiltViewModel
class ExerciseCatalogViewModel @Inject constructor(
    private val catalog: ExerciseCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    // Deferred delete: hidden from the list while the undo snackbar is shown.
    private val _pendingDelete = MutableStateFlow<Exercise?>(null)

    val filterOptions: StateFlow<ExerciseFilterOptions> = catalog.exercises()
        .map { list ->
            ExerciseFilterOptions(
                muscles = list.flatMap { it.primaryMuscles }.distinct().sorted(),
                equipment = list.mapNotNull { it.equipment }.distinct().sorted(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseFilterOptions())

    val exercises: StateFlow<List<Exercise>> = combine(
        catalog.exercises(),
        _uiState,
        _pendingDelete,
    ) { list, state, pending ->
        list.asSequence()
            .filter { pending == null || it.stableId != pending.stableId }
            .filter { state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) }
            .filter { state.muscle == null || state.muscle in it.primaryMuscles }
            .filter { state.equipment == null || it.equipment == state.equipment }
            .sortedBy { it.name.lowercase() }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    /** Toggle a muscle filter (selecting the active one clears it). */
    fun onMuscleSelected(muscle: String?) = _uiState.update {
        it.copy(muscle = if (it.muscle == muscle) null else muscle)
    }

    /** Toggle an equipment filter (selecting the active one clears it). */
    fun onEquipmentSelected(equipment: String?) = _uiState.update {
        it.copy(equipment = if (it.equipment == equipment) null else equipment)
    }

    fun clearFilters() = _uiState.update { it.copy(muscle = null, equipment = null) }

    /**
     * Create or update a custom exercise. [stableId] is null for a new one (a fresh UUID is minted)
     * and carries the existing id when editing. Catalog exercises are read-only and never reach here.
     */
    fun saveCustomExercise(
        stableId: String?,
        name: String,
        type: ExerciseType,
        primaryMuscles: List<String>,
        equipment: String?,
        mechanic: Mechanic?,
        instructions: List<String>,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val exercise = Exercise(
            stableId = stableId ?: UUID.randomUUID().toString(),
            name = trimmedName,
            type = type,
            isCustom = true,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = emptyList(),
            equipment = equipment?.trim()?.takeIf { it.isNotEmpty() },
            mechanic = mechanic,
            category = null,
            instructions = instructions,
            restSeconds = null,
        )
        viewModelScope.launch { catalog.upsertAll(listOf(exercise)) }
    }

    // --- deferred delete (custom exercises only) ---

    fun deletePending(exercise: Exercise) {
        _pendingDelete.value = exercise
    }

    fun confirmDelete(exercise: Exercise) {
        if (_pendingDelete.value?.stableId == exercise.stableId) _pendingDelete.value = null
        viewModelScope.launch { catalog.delete(exercise.stableId) }
    }

    fun undoDelete(exercise: Exercise) {
        if (_pendingDelete.value?.stableId == exercise.stableId) _pendingDelete.value = null
    }
}
