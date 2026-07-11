package dev.antonlammers.macrotrac.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.InlineHistory
import dev.antonlammers.macrotrac.domain.SetPerformance
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutSessionRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** One exercise inside the live session, joined with its catalog name + type for display. */
data class SessionExerciseUi(
    /** Stable per-row key (DB id when resumed, a synthetic client id for in-session additions). */
    val id: Long,
    val exerciseStableId: String,
    val name: String,
    val type: ExerciseType,
    val sets: List<SetEntry>,
    /** Values logged for this exercise last time, shown as per-set placeholder hints (spec §3.3). */
    val lastPerformance: List<SetPerformance> = emptyList(),
)

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val exercises: List<SessionExerciseUi> = emptyList(),
)

/**
 * Drives a live training session (spec §3.3; no rest-timer or volume/1RM/PR calculations yet). The
 * single active session is the source of truth and is **persisted on every change** (continuous
 * persistence), so it survives app death and is resumable. On entry the VM resumes the existing
 * active session if there is one; otherwise it creates a fresh one (empty or seeded from a template)
 * and immediately persists it. Each set carries a [SetType]; the ui state also exposes each
 * exercise's last-training values ([InlineHistory]) as placeholder hints. Finishing marks it
 * completed; discarding deletes it.
 */
@HiltViewModel
class WorkoutSessionViewModel(
    private val sessions: WorkoutSessionRepository,
    private val templates: WorkoutTemplateRepository,
    private val catalog: ExerciseCatalogRepository,
    private val templateId: Long,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        sessions: WorkoutSessionRepository,
        templates: WorkoutTemplateRepository,
        catalog: ExerciseCatalogRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        sessions,
        templates,
        catalog,
        savedStateHandle.get<Long>("templateId") ?: 0L,
        { System.currentTimeMillis() },
    )

    private val _session = MutableStateFlow<WorkoutSession?>(null)

    private val _finished = MutableStateFlow(false)
    /** Flips to true once the session is finished or discarded, signalling the screen to pop back. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    // Serializes saves so the first insert assigns the row id before any follow-up save reuses it.
    private val persistMutex = Mutex()

    // Synthetic ids for in-session additions, giving each exercise/set a stable UI key (for LazyColumn
    // keys and per-field text state) that survives reordering. Negative so they never clash with the
    // positive DB ids a resumed session carries. Not persisted — the mappers auto-generate row ids.
    private var nextClientId = -1L
    private fun newId(): Long = nextClientId--

    val uiState: StateFlow<WorkoutSessionUiState> = combine(
        _session,
        catalog.exercises(),
        sessions.sessions(),
    ) { session, catalogExercises, history ->
        if (session == null) {
            WorkoutSessionUiState(loading = true)
        } else {
            val byStableId = catalogExercises.associateBy { it.stableId }
            WorkoutSessionUiState(
                loading = false,
                exercises = session.exercises.map { se ->
                    val exercise = byStableId[se.exerciseStableId]
                    SessionExerciseUi(
                        id = se.id,
                        exerciseStableId = se.exerciseStableId,
                        name = exercise?.name ?: se.exerciseStableId,
                        type = exercise?.type ?: ExerciseType.WEIGHT_REPS,
                        sets = se.sets,
                        lastPerformance = InlineHistory.lastPerformance(history, se.exerciseStableId),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSessionUiState(loading = true))

    // --- exercise picker (add-from-catalog sheet), same shape as the template editor ---

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

    fun onPickerQueryChange(query: String) = _pickerQuery.update { query }

    init {
        viewModelScope.launch {
            val active = sessions.activeSession().first()
            if (active != null) {
                _session.value = active
            } else {
                _session.value = if (templateId != 0L) buildFromTemplate(templateId) else buildEmpty()
                persist()
            }
        }
    }

    private fun buildEmpty(): WorkoutSession = WorkoutSession(
        stableId = UUID.randomUUID().toString(),
        date = today(),
        isActive = true,
        startedAtMs = clock(),
    )

    private suspend fun buildFromTemplate(id: Long): WorkoutSession {
        val template = templates.template(id).first()
        val exercises = template?.exercises
            ?.sortedBy { it.position }
            ?.mapIndexed { index, te ->
                SessionExercise(
                    id = newId(),
                    exerciseStableId = te.exerciseStableId,
                    position = index,
                    sets = List(te.targetSets) { setIndex ->
                        SetEntry(id = newId(), position = setIndex, weightKg = 0.0, reps = 0)
                    },
                )
            }
            .orEmpty()
        return buildEmpty().copy(exercises = exercises)
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()

    // --- structural mutations (each one re-persists immediately) ---

    fun addExercise(exercise: Exercise) = mutate { session ->
        val newExercise = SessionExercise(
            id = newId(),
            exerciseStableId = exercise.stableId,
            position = session.exercises.size,
            sets = listOf(SetEntry(id = newId(), position = 0, weightKg = 0.0, reps = 0)),
        )
        session.copy(exercises = reindexExercises(session.exercises + newExercise))
    }

    fun removeExercise(exerciseIndex: Int) = mutate { session ->
        if (exerciseIndex !in session.exercises.indices) return@mutate session
        session.copy(exercises = reindexExercises(session.exercises.filterIndexed { i, _ -> i != exerciseIndex }))
    }

    fun addSet(exerciseIndex: Int) = mutateExercise(exerciseIndex) { exercise ->
        exercise.copy(
            sets = reindexSets(exercise.sets + SetEntry(id = newId(), position = exercise.sets.size, weightKg = 0.0, reps = 0)),
        )
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) = mutateExercise(exerciseIndex) { exercise ->
        if (setIndex !in exercise.sets.indices) return@mutateExercise exercise
        exercise.copy(sets = reindexSets(exercise.sets.filterIndexed { i, _ -> i != setIndex }))
    }

    fun moveSetUp(exerciseIndex: Int, setIndex: Int) = swapSets(exerciseIndex, setIndex, setIndex - 1)
    fun moveSetDown(exerciseIndex: Int, setIndex: Int) = swapSets(exerciseIndex, setIndex, setIndex + 1)

    private fun swapSets(exerciseIndex: Int, a: Int, b: Int) = mutateExercise(exerciseIndex) { exercise ->
        if (a !in exercise.sets.indices || b !in exercise.sets.indices) return@mutateExercise exercise
        exercise.copy(
            sets = reindexSets(
                exercise.sets.toMutableList().apply { val tmp = this[a]; this[a] = this[b]; this[b] = tmp },
            ),
        )
    }

    fun setWeight(exerciseIndex: Int, setIndex: Int, weightKg: Double) =
        mutateSet(exerciseIndex, setIndex) { it.copy(weightKg = weightKg) }

    fun setReps(exerciseIndex: Int, setIndex: Int, reps: Int) =
        mutateSet(exerciseIndex, setIndex) { it.copy(reps = reps) }

    fun toggleSetCompleted(exerciseIndex: Int, setIndex: Int) =
        mutateSet(exerciseIndex, setIndex) { it.copy(completed = !it.completed) }

    fun setSetType(exerciseIndex: Int, setIndex: Int, type: SetType) =
        mutateSet(exerciseIndex, setIndex) { it.copy(type = type) }

    // --- lifecycle ---

    fun finish() {
        _session.update { it?.copy(isActive = false, endedAtMs = clock()) }
        viewModelScope.launch {
            persist()
            _finished.value = true
        }
    }

    fun discard() {
        val current = _session.value
        viewModelScope.launch {
            if (current != null && current.id != 0L) sessions.delete(current.id)
            _finished.value = true
        }
    }

    // --- helpers ---

    private fun mutate(block: (WorkoutSession) -> WorkoutSession) {
        val current = _session.value ?: return
        _session.value = block(current)
        persistAsync()
    }

    private fun mutateExercise(exerciseIndex: Int, block: (SessionExercise) -> SessionExercise) = mutate { session ->
        if (exerciseIndex !in session.exercises.indices) return@mutate session
        session.copy(exercises = session.exercises.mapIndexed { i, e -> if (i == exerciseIndex) block(e) else e })
    }

    private fun mutateSet(exerciseIndex: Int, setIndex: Int, block: (SetEntry) -> SetEntry) =
        mutateExercise(exerciseIndex) { exercise ->
            if (setIndex !in exercise.sets.indices) exercise
            else exercise.copy(sets = exercise.sets.mapIndexed { i, s -> if (i == setIndex) block(s) else s })
        }

    private fun reindexExercises(list: List<SessionExercise>) = list.mapIndexed { i, e -> e.copy(position = i) }
    private fun reindexSets(list: List<SetEntry>) = list.mapIndexed { i, s -> s.copy(position = i) }

    private fun persistAsync() = viewModelScope.launch { persist() }

    private suspend fun persist() = persistMutex.withLock {
        val current = _session.value ?: return@withLock
        val id = sessions.save(current)
        if (current.id != id) _session.update { it?.copy(id = id) }
    }
}
