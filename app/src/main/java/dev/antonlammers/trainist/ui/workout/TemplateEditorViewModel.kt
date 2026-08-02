package dev.antonlammers.trainist.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.SupersetPlacement
import dev.antonlammers.trainist.domain.Supersets
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.SupersetMember
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.WorkoutTemplateRepository
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

/** One exercise slot being edited: the referenced exercise plus its planned sets, in order. */
data class TemplateSlot(
    /** Stable per-slot client id (survives drag reordering); never persisted. */
    val id: Long,
    val exerciseStableId: String,
    val exerciseName: String,
    val setTypes: List<SetType>,
    override val supersetGroupId: Int? = null,
) : SupersetMember<TemplateSlot> {
    override fun withSupersetGroupId(groupId: Int?) = copy(supersetGroupId = groupId)
}

data class TemplateEditorUiState(
    val name: String = "",
    val slots: List<TemplateSlot> = emptyList(),
    val loading: Boolean = false,
) {
    /** A template needs a name and at least one exercise before it can be saved. */
    val canSave: Boolean get() = name.isNotBlank() && slots.isNotEmpty()

    /** Slot index → its place in a planned superset; slots that stand alone are absent. */
    val supersets: Map<Int, SupersetPlacement> get() = Supersets.placements(slots)
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

    // Synthetic, never-persisted per-slot ids — only used as stable Compose keys for drag reorder.
    private var nextClientId = -1L
    private fun newId(): Long = nextClientId--

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
                            id = newId(),
                            exerciseStableId = e.exerciseStableId,
                            exerciseName = names[e.exerciseStableId] ?: e.exerciseStableId,
                            setTypes = e.setTypes,
                            supersetGroupId = e.supersetGroupId,
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
                id = newId(),
                exerciseStableId = exercise.stableId,
                exerciseName = exercise.name,
                setTypes = List(DEFAULT_TARGET_SETS) { SetType.NORMAL },
            ),
        )
    }

    fun removeExercise(index: Int) = _uiState.update { state ->
        if (index !in state.slots.indices) state
        // Normalize so a planned superset whose partner just went away dissolves instead of
        // lingering as a group of one (see Supersets).
        else state.copy(slots = Supersets.normalize(state.slots.filterIndexed { i, _ -> i != index }))
    }

    /** Plans the slot at [index] and the one after it as a superset. */
    fun groupWithNext(index: Int) = _uiState.update { state ->
        state.copy(slots = Supersets.groupWithNext(state.slots, index))
    }

    /** Takes the slot at [index] back out of its planned superset. */
    fun ungroup(index: Int) = _uiState.update { state ->
        state.copy(slots = Supersets.ungroup(state.slots, index))
    }

    /** Appends a planned set (default [SetType.NORMAL]) to the slot, up to [MAX_TARGET_SETS]. */
    fun addSet(index: Int) = _uiState.update { state ->
        val slot = state.slots.getOrNull(index) ?: return@update state
        if (slot.setTypes.size >= MAX_TARGET_SETS) return@update state
        state.copy(
            slots = state.slots.mapIndexed { i, s ->
                if (i == index) s.copy(setTypes = s.setTypes + SetType.NORMAL) else s
            },
        )
    }

    /** Removes the planned set at [setIndex], keeping at least [MIN_TARGET_SETS]. */
    fun removeSet(index: Int, setIndex: Int) = _uiState.update { state ->
        val slot = state.slots.getOrNull(index) ?: return@update state
        if (setIndex !in slot.setTypes.indices || slot.setTypes.size <= MIN_TARGET_SETS) return@update state
        state.copy(
            slots = state.slots.mapIndexed { i, s ->
                if (i == index) s.copy(setTypes = s.setTypes.filterIndexed { si, _ -> si != setIndex }) else s
            },
        )
    }

    /** Changes the planned type of the set at [setIndex]. */
    fun setSetType(index: Int, setIndex: Int, type: SetType) = _uiState.update { state ->
        val slot = state.slots.getOrNull(index) ?: return@update state
        if (setIndex !in slot.setTypes.indices) return@update state
        state.copy(
            slots = state.slots.mapIndexed { i, s ->
                if (i == index) {
                    s.copy(setTypes = s.setTypes.mapIndexed { si, t -> if (si == setIndex) type else t })
                } else s
            },
        )
    }

    /** Swap two slots — called repeatedly (once per adjacent step) while a slot is dragged into place. */
    fun moveSlot(from: Int, to: Int) = _uiState.update { state ->
        if (from !in state.slots.indices || to !in state.slots.indices) return@update state
        state.copy(
            // Reordering can pull a group's members apart; normalize re-derives the grouping from the
            // new adjacency rather than letting a split group survive as two distant halves.
            slots = Supersets.normalize(
                state.slots.toMutableList().apply {
                    val tmp = this[from]; this[from] = this[to]; this[to] = tmp
                },
            ),
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
                    setTypes = slot.setTypes,
                    supersetGroupId = slot.supersetGroupId,
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
