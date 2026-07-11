package dev.antonlammers.macrotrac.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One exercise slot being edited: the referenced exercise plus its planned target-set count. */
data class TemplateSlot(
    val exerciseStableId: String,
    val exerciseName: String,
    val targetSets: Int,
)

data class TemplateEditorUiState(
    val name: String = "",
    val slots: List<TemplateSlot> = emptyList(),
    val loading: Boolean = false,
) {
    /** A template needs a name and at least one exercise before it can be saved. */
    val canSave: Boolean get() = name.isNotBlank() && slots.isNotEmpty()
}

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    private val templates: WorkoutTemplateRepository,
    private val catalog: ExerciseCatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 0 = creating a new template; otherwise the existing row id being edited. */
    private val templateId: Long = savedStateHandle.get<Long>("templateId") ?: 0L

    /** Reused when saving an existing template so its backup-stable key is preserved. */
    private var stableId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _uiState.asStateFlow()

    private val _saved = MutableStateFlow(false)
    /** Flips to true once the template is persisted, signalling the screen to pop back. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    // --- exercise picker (add-from-catalog sheet) ---

    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    val pickerResults: StateFlow<List<Exercise>> = combine(
        catalog.exercises(),
        _pickerQuery,
    ) { list, query ->
        list.asSequence()
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isNewTemplate: Boolean get() = templateId == 0L

    init {
        if (templateId != 0L) {
            _uiState.update { it.copy(loading = true) }
            viewModelScope.launch { load(templateId) }
        }
    }

    private suspend fun load(id: Long) {
        val template = templates.template(id).first()
        if (template == null) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        stableId = template.stableId
        val names = catalog.exercises().first().associate { it.stableId to it.name }
        _uiState.update {
            it.copy(
                name = template.name,
                slots = template.exercises
                    .sortedBy { e -> e.position }
                    .map { e ->
                        TemplateSlot(
                            exerciseStableId = e.exerciseStableId,
                            exerciseName = names[e.exerciseStableId] ?: e.exerciseStableId,
                            targetSets = e.targetSets,
                        )
                    },
                loading = false,
            )
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onPickerQueryChange(query: String) = _pickerQuery.update { query }

    /** Append an exercise from the catalog. Duplicates are allowed (same exercise, different slot). */
    fun addExercise(exercise: Exercise) = _uiState.update { state ->
        state.copy(
            slots = state.slots + TemplateSlot(
                exerciseStableId = exercise.stableId,
                exerciseName = exercise.name,
                targetSets = DEFAULT_TARGET_SETS,
            ),
        )
    }

    fun removeExercise(index: Int) = _uiState.update { state ->
        if (index !in state.slots.indices) state
        else state.copy(slots = state.slots.filterIndexed { i, _ -> i != index })
    }

    fun setTargetSets(index: Int, targetSets: Int) = _uiState.update { state ->
        if (index !in state.slots.indices) return@update state
        val clamped = targetSets.coerceIn(MIN_TARGET_SETS, MAX_TARGET_SETS)
        state.copy(
            slots = state.slots.mapIndexed { i, slot ->
                if (i == index) slot.copy(targetSets = clamped) else slot
            },
        )
    }

    fun moveUp(index: Int) = swap(index, index - 1)
    fun moveDown(index: Int) = swap(index, index + 1)

    private fun swap(a: Int, b: Int) = _uiState.update { state ->
        if (a !in state.slots.indices || b !in state.slots.indices) return@update state
        state.copy(
            slots = state.slots.toMutableList().apply {
                val tmp = this[a]; this[a] = this[b]; this[b] = tmp
            },
        )
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val template = WorkoutTemplate(
            id = templateId,
            stableId = stableId,
            name = state.name.trim(),
            exercises = state.slots.mapIndexed { index, slot ->
                TemplateExercise(
                    exerciseStableId = slot.exerciseStableId,
                    position = index,
                    targetSets = slot.targetSets,
                )
            },
        )
        viewModelScope.launch {
            templates.save(template)
            _saved.value = true
        }
    }

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val MIN_TARGET_SETS = 1
        const val MAX_TARGET_SETS = 20
    }
}
