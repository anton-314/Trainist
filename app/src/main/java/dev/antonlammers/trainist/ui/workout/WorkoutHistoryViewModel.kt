package dev.antonlammers.trainist.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.WorkoutMetrics
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One completed exercise as the history detail renders it — joined with catalog + calculations. */
data class HistoryExerciseUi(
    /** Stable per-row UI key (the persisted session-exercise id, or a synthetic id for added rows). */
    val id: Long,
    val exerciseStableId: String,
    val name: String,
    val type: ExerciseType,
    val sets: List<SetEntry>,
    val volumeKg: Double,
    val estimatedOneRepMaxKg: Double?,
    /** True when this exercise set a new max-weight PR in this session. */
    val isPersonalRecord: Boolean,
)

/** One session on the selected day. */
data class HistorySessionUi(
    val id: Long,
    val startedAtMs: Long,
    val totalVolumeKg: Double,
    val exercises: List<HistoryExerciseUi>,
)

data class WorkoutHistoryUiState(
    val displayedMonth: YearMonth,
    val trainingDays: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val selectedSessions: List<HistorySessionUi> = emptyList(),
)

/**
 * Drives the training history: a month calendar with training-day markers, the sessions
 * of the selected day (with volume / estimated 1RM / PR badges), and in-place editing of a past
 * session's sets (weight, reps, set-type, add/remove set) plus session deletion with an undo window.
 *
 * The selected day's sessions are held in a **local editable copy** ([_editable]) — like the live
 * session's `_session` — because [WorkoutSessionRepository.save] rewrites the whole set graph and
 * reassigns child row ids, so driving the editable rows straight off the repo flow would reset their
 * per-field text state on every keystroke. The copy is (re)seeded only when the selected day changes;
 * edits mutate it locally and persist immediately. The calendar markers and the PR sweep, by
 * contrast, stay reactive off the repository. The `clock` is injected for testable "today"/month.
 */
@HiltViewModel
class WorkoutHistoryViewModel(
    private val sessions: WorkoutSessionRepository,
    private val catalog: ExerciseCatalogRepository,
    private val weight: WeightRepository,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        sessions: WorkoutSessionRepository,
        catalog: ExerciseCatalogRepository,
        weight: WeightRepository,
    ) : this(sessions, catalog, weight, { System.currentTimeMillis() })

    private val _month = MutableStateFlow(currentMonth())
    private val _selectedDate = MutableStateFlow<LocalDate?>(today())
    private val _editable = MutableStateFlow<List<WorkoutSession>>(emptyList())
    private val _weightEntries = MutableStateFlow<List<WeightEntry>>(emptyList())
    private val _pendingDelete = MutableStateFlow<Long?>(null)

    private val persistMutex = Mutex()

    // Synthetic negative ids for rows added while editing (stable UI keys; never persisted — the
    // mappers regenerate real row ids on save), mirroring the live session's scheme.
    private var nextClientId = -1L
    private fun newId(): Long = nextClientId--

    // All completed sessions (the history feed), minus the one pending deletion during its undo window.
    private val completedHistory = combine(sessions.sessions(), _pendingDelete) { all, pending ->
        all.filter { !it.isActive && it.id != pending }
    }

    // (sessionStableId, exerciseStableId) pairs that set a max-weight PR — swept over the full history.
    private val prRecords = combine(completedHistory, catalog.exercises(), _weightEntries) { history, catalogList, weights ->
        val byStableId = catalogList.associateBy { it.stableId }
        WorkoutMetrics.personalRecords(
            history,
            typeOf = { byStableId[it]?.type ?: ExerciseType.WEIGHT_REPS },
            bodyWeightForDate = { WorkoutMetrics.resolveBodyWeightKg(weights, it) },
        )
    }

    private val trainingDays = combine(_month, completedHistory) { month, history ->
        history.asSequence().map { it.date }.filter { YearMonth.from(it) == month }.toSet()
    }

    private val detailSessions = combine(
        _editable,
        _pendingDelete,
        catalog.exercises(),
        _weightEntries,
        prRecords,
    ) { editable, pending, catalogList, weights, prs ->
        val byStableId = catalogList.associateBy { it.stableId }
        editable
            .filter { it.id != pending }
            .sortedBy { it.startedAtMs }
            .map { session ->
                val bodyWeightKg = WorkoutMetrics.resolveBodyWeightKg(weights, session.date)
                val exercises = session.exercises.map { se ->
                    val exercise = byStableId[se.exerciseStableId]
                    val type = exercise?.type ?: ExerciseType.WEIGHT_REPS
                    HistoryExerciseUi(
                        id = se.id,
                        exerciseStableId = se.exerciseStableId,
                        name = exercise?.name ?: se.exerciseStableId,
                        type = type,
                        sets = se.sets,
                        volumeKg = WorkoutMetrics.volumeKg(se.sets, type, bodyWeightKg),
                        estimatedOneRepMaxKg = WorkoutMetrics.bestEstimatedOneRepMaxKg(se.sets, type, bodyWeightKg),
                        isPersonalRecord = (session.stableId to se.exerciseStableId) in prs,
                    )
                }
                HistorySessionUi(
                    id = session.id,
                    startedAtMs = session.startedAtMs,
                    totalVolumeKg = exercises.sumOf { it.volumeKg },
                    exercises = exercises,
                )
            }
    }

    val uiState: StateFlow<WorkoutHistoryUiState> = combine(
        _month,
        _selectedDate,
        trainingDays,
        detailSessions,
    ) { month, date, days, detail ->
        WorkoutHistoryUiState(month, days, date, detail)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WorkoutHistoryUiState(currentMonth(), selectedDate = today()),
    )

    init {
        viewModelScope.launch { _weightEntries.value = weight.allEntries() }
        // Reseed the editable copy from the persisted sessions whenever the selected day changes
        // (never on plain repo re-emits, so in-progress edits are not clobbered).
        viewModelScope.launch {
            _selectedDate.collect { date ->
                _editable.value = if (date == null) {
                    emptyList()
                } else {
                    sessions.sessions().first()
                        .filter { !it.isActive && it.date == date }
                        .sortedBy { it.startedAtMs }
                }
            }
        }
    }

    // --- calendar navigation ---

    fun showPreviousMonth() = _month.update { it.minusMonths(1) }
    fun showNextMonth() = _month.update { it.plusMonths(1) }
    fun selectDate(date: LocalDate) {
        _month.value = YearMonth.from(date)
        _selectedDate.value = date
    }

    // --- editing a past session's sets ---

    fun setWeight(sessionId: Long, exerciseIndex: Int, setIndex: Int, weightKg: Double) =
        mutateSet(sessionId, exerciseIndex, setIndex) { it.copy(weightKg = weightKg) }

    fun setReps(sessionId: Long, exerciseIndex: Int, setIndex: Int, reps: Int) =
        mutateSet(sessionId, exerciseIndex, setIndex) { it.copy(reps = reps) }

    fun setSetType(sessionId: Long, exerciseIndex: Int, setIndex: Int, type: SetType) =
        mutateSet(sessionId, exerciseIndex, setIndex) { it.copy(type = type) }

    fun addSet(sessionId: Long, exerciseIndex: Int) = mutateExercise(sessionId, exerciseIndex) { exercise ->
        exercise.copy(
            sets = reindexSets(exercise.sets + SetEntry(id = newId(), position = exercise.sets.size, weightKg = 0.0, reps = 0)),
        )
    }

    fun removeSet(sessionId: Long, exerciseIndex: Int, setIndex: Int) = mutateExercise(sessionId, exerciseIndex) { exercise ->
        if (setIndex !in exercise.sets.indices) exercise
        else exercise.copy(sets = reindexSets(exercise.sets.filterIndexed { i, _ -> i != setIndex }))
    }

    // --- delete a session (deferred, with undo) ---

    fun deletePending(sessionId: Long) {
        _pendingDelete.value = sessionId
    }

    fun confirmDelete(sessionId: Long) {
        if (_pendingDelete.value == sessionId) _pendingDelete.value = null
        _editable.update { list -> list.filterNot { it.id == sessionId } }
        viewModelScope.launch { sessions.delete(sessionId) }
    }

    fun undoDelete(sessionId: Long) {
        if (_pendingDelete.value == sessionId) _pendingDelete.value = null
    }

    // --- helpers ---

    private fun mutateExercise(sessionId: Long, exerciseIndex: Int, block: (SessionExercise) -> SessionExercise) {
        val current = _editable.value.firstOrNull { it.id == sessionId } ?: return
        if (exerciseIndex !in current.exercises.indices) return
        val updated = current.copy(
            exercises = current.exercises.mapIndexed { i, e -> if (i == exerciseIndex) block(e) else e },
        )
        _editable.update { list -> list.map { if (it.id == sessionId) updated else it } }
        persist(updated)
    }

    private fun mutateSet(sessionId: Long, exerciseIndex: Int, setIndex: Int, block: (SetEntry) -> SetEntry) =
        mutateExercise(sessionId, exerciseIndex) { exercise ->
            if (setIndex !in exercise.sets.indices) exercise
            else exercise.copy(sets = exercise.sets.mapIndexed { i, s -> if (i == setIndex) block(s) else s })
        }

    private fun reindexSets(list: List<SetEntry>) = list.mapIndexed { i, s -> s.copy(position = i) }

    private fun persist(session: WorkoutSession) {
        viewModelScope.launch { persistMutex.withLock { sessions.save(session) } }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun currentMonth(): YearMonth = YearMonth.from(today())
}
