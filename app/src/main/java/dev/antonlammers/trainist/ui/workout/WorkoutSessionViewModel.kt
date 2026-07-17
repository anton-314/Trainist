package dev.antonlammers.trainist.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.InlineHistory
import dev.antonlammers.trainist.domain.RestTimer
import dev.antonlammers.trainist.domain.SetPerformance
import dev.antonlammers.trainist.domain.TemplateUpdate
import dev.antonlammers.trainist.domain.WorkoutMetrics
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import dev.antonlammers.trainist.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
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
    /** Values logged for this exercise last time, shown as per-set placeholder hints. */
    val lastPerformance: List<SetPerformance> = emptyList(),
    /** Rest duration in seconds for this exercise (per-exercise override, or the global default). */
    val restSeconds: Int = RestTimer.DEFAULT_REST_SECONDS,
    /** Σ (effective weight × reps) over the non-warm-up sets so far. */
    val volumeKg: Double = 0.0,
    /** Highest Epley estimated 1RM over the performed sets, or null if nothing has been logged. */
    val estimatedOneRepMaxKg: Double? = null,
)

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val exercises: List<SessionExerciseUi> = emptyList(),
    /** Body weight applied to bodyweight-exercise calculations (resolved for the session date). */
    val bodyWeightKg: Double? = null,
)

/** The running rest timer as the screen renders it. */
data class RestTimerUiState(
    val exerciseName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isPaused: Boolean,
)

/** VM-side wrapper pairing the pure [RestTimer] with the exercise it belongs to (for the label). */
private data class ActiveRest(val exerciseStableId: String, val exerciseName: String, val timer: RestTimer)

/**
 * One-shot command the screen turns into WorkManager scheduling + the Android notification (keeps
 * the VM Android-free). [Start] covers both starting and resuming a running countdown: the screen
 * (re)schedules the alerting "rest over" notification for [delayMs] from now and shows/updates the
 * ongoing "still resting" notification counting down to [endAtMs]. [Pause] freezes both: the pending
 * alert is cancelled and the ongoing notification switches to a static "paused" display. [Cancel]
 * stops everything (skip/finish/discard) — cancels the pending alert and dismisses the notification.
 */
sealed interface RestCommand {
    data class Start(val exerciseName: String, val totalSeconds: Int, val delayMs: Long, val endAtMs: Long) : RestCommand
    data class Pause(val exerciseName: String, val remainingSeconds: Int) : RestCommand
    data object Cancel : RestCommand
}

/**
 * Drives a live training session. The single active
 * session is the source of truth and is **persisted on every change** (continuous persistence), so
 * it survives app death and is resumable. On entry the VM resumes the existing active session if
 * there is one; otherwise it creates a fresh one (empty or seeded from a template) and immediately
 * persists it. Each set carries a [SetType]; the ui state also exposes each exercise's last-training
 * values ([InlineHistory]) as placeholder hints. Checking a set off starts the [RestTimer] (per-
 * exercise duration, default 90 s), whose background notification the screen schedules via the
 * emitted [RestCommand]s. Finishing marks it completed; discarding deletes it.
 */
@HiltViewModel
class WorkoutSessionViewModel(
    private val sessions: WorkoutSessionRepository,
    private val templates: WorkoutTemplateRepository,
    private val catalog: ExerciseCatalogRepository,
    private val weight: WeightRepository,
    private val templateId: Long,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        sessions: WorkoutSessionRepository,
        templates: WorkoutTemplateRepository,
        catalog: ExerciseCatalogRepository,
        weight: WeightRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        sessions,
        templates,
        catalog,
        weight,
        savedStateHandle.get<Long>("templateId") ?: 0L,
        { System.currentTimeMillis() },
    )

    private val _session = MutableStateFlow<WorkoutSession?>(null)

    // Resolved body weight for the session date, feeding bodyweight-exercise volume/1RM.
    private val _bodyWeightKg = MutableStateFlow<Double?>(null)

    private val _finished = MutableStateFlow(false)
    /** Flips to true once the session is finished or discarded, signalling the screen to pop back. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _pendingTemplateUpdate = MutableStateFlow<WorkoutTemplate?>(null)
    /**
     * Set on finish when a template-based session diverged structurally from its template: holds the
     * merged template to offer as an update ([TemplateUpdate]). The screen shows a confirm dialog and
     * calls [confirmTemplateUpdate]/[dismissTemplateUpdate]; null when there is nothing to offer.
     */
    val pendingTemplateUpdate: StateFlow<WorkoutTemplate?> = _pendingTemplateUpdate.asStateFlow()

    // --- rest timer ---

    private val _rest = MutableStateFlow<ActiveRest?>(null)

    // One-shot WorkManager schedule/cancel commands; the screen (which has a Context) executes them.
    private val restCommandChannel = Channel<RestCommand>(Channel.BUFFERED)
    val restCommands: Flow<RestCommand> = restCommandChannel.receiveAsFlow()

    /**
     * The running rest timer, ticked once a second from the wall clock. Emits null when idle and
     * auto-clears itself when the countdown reaches zero (the background notification, if any, is
     * left to fire on its own — a finish must not cancel it).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val restTimer: StateFlow<RestTimerUiState?> = _rest.flatMapLatest { active ->
        when {
            active == null -> flowOf(null)
            active.timer.isPaused -> flowOf(active.toUi(active.timer.remainingMs(clock())))
            else -> flow {
                while (true) {
                    val remainingMs = active.timer.remainingMs(clock())
                    emit(active.toUi(remainingMs))
                    if (remainingMs <= 0L) break
                    delay(TICK_MS)
                }
                _rest.value = null
                persistRest(null, null)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun ActiveRest.toUi(remainingMs: Long) = RestTimerUiState(
        exerciseName = exerciseName,
        totalSeconds = timer.totalSeconds,
        remainingSeconds = ceil(remainingMs / 1000.0).toInt(),
        isPaused = timer.isPaused,
    )

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
        _bodyWeightKg,
    ) { session, catalogExercises, history, bodyWeightKg ->
        if (session == null) {
            WorkoutSessionUiState(loading = true)
        } else {
            val byStableId = catalogExercises.associateBy { it.stableId }
            WorkoutSessionUiState(
                loading = false,
                bodyWeightKg = bodyWeightKg,
                exercises = session.exercises.map { se ->
                    val exercise = byStableId[se.exerciseStableId]
                    val type = exercise?.type ?: ExerciseType.WEIGHT_REPS
                    SessionExerciseUi(
                        id = se.id,
                        exerciseStableId = se.exerciseStableId,
                        name = exercise?.name ?: se.exerciseStableId,
                        type = type,
                        sets = se.sets,
                        lastPerformance = InlineHistory.lastPerformance(history, se.exerciseStableId),
                        restSeconds = exercise?.restSeconds ?: RestTimer.DEFAULT_REST_SECONDS,
                        volumeKg = WorkoutMetrics.volumeKg(se.sets, type, bodyWeightKg),
                        estimatedOneRepMaxKg = WorkoutMetrics.bestEstimatedOneRepMaxKg(se.sets, type, bodyWeightKg),
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
            _bodyWeightKg.value = WorkoutMetrics.resolveBodyWeightKg(weight.allEntries(), today())
        }
        viewModelScope.launch {
            val active = sessions.activeSession().first()
            if (active != null) {
                _session.value = active
                restoreRestTimer(active)
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
                    sets = te.setTypes.mapIndexed { setIndex, type ->
                        SetEntry(id = newId(), position = setIndex, weightKg = 0.0, reps = 0, type = type)
                    },
                )
            }
            .orEmpty()
        return buildEmpty().copy(exercises = exercises, templateStableId = template?.stableId)
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

    /** Swap two sets within an exercise — called repeatedly (once per adjacent step) while a set is dragged into place. */
    fun moveSet(exerciseIndex: Int, from: Int, to: Int) = mutateExercise(exerciseIndex) { exercise ->
        if (from !in exercise.sets.indices || to !in exercise.sets.indices) return@mutateExercise exercise
        exercise.copy(
            sets = reindexSets(
                exercise.sets.toMutableList().apply { val tmp = this[from]; this[from] = this[to]; this[to] = tmp },
            ),
        )
    }

    fun setWeight(exerciseIndex: Int, setIndex: Int, weightKg: Double) =
        mutateSet(exerciseIndex, setIndex) { it.copy(weightKg = weightKg) }

    fun setReps(exerciseIndex: Int, setIndex: Int, reps: Int) =
        mutateSet(exerciseIndex, setIndex) { it.copy(reps = reps) }

    fun toggleSetCompleted(exerciseIndex: Int, setIndex: Int) {
        val session = _session.value ?: return
        val exercise = session.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.sets.getOrNull(setIndex) ?: return
        // Unchecking never starts a rest and never touches the values.
        if (set.completed) {
            mutateSet(exerciseIndex, setIndex) { it.copy(completed = false) }
            return
        }
        // Checking a set off with empty fields adopts the inline-history "last time" placeholder as if
        // it had been typed (spec addendum — one less thing to fill in), then starts the rest timer.
        viewModelScope.launch {
            val hint = InlineHistory.placeholderForSet(
                InlineHistory.lastPerformance(sessions.sessions().first(), exercise.exerciseStableId),
                setIndex,
            )
            mutateSet(exerciseIndex, setIndex) { s ->
                s.copy(
                    completed = true,
                    weightKg = if (s.weightKg == 0.0 && hint != null) hint.weightKg else s.weightKg,
                    reps = if (s.reps == 0 && hint != null) hint.reps else s.reps,
                )
            }
            startRest(exercise.exerciseStableId)
        }
    }

    fun setSetType(exerciseIndex: Int, setIndex: Int, type: SetType) =
        mutateSet(exerciseIndex, setIndex) { it.copy(type = type) }

    // --- rest timer control ---

    private fun startRest(exerciseStableId: String) {
        viewModelScope.launch {
            val exercise = catalog.exercise(exerciseStableId).first()
            val seconds = exercise?.restSeconds ?: RestTimer.DEFAULT_REST_SECONDS
            val timer = RestTimer.start(clock(), seconds)
            val name = exercise?.name ?: exerciseStableId
            _rest.value = ActiveRest(exerciseStableId, name, timer)
            persistRest(exerciseStableId, timer)
            restCommandChannel.send(startCommand(name, timer))
        }
    }

    fun pauseRest() {
        val active = _rest.value ?: return
        if (active.timer.isPaused) return
        val paused = active.timer.paused(clock())
        _rest.value = active.copy(timer = paused)
        persistRest(active.exerciseStableId, paused)
        restCommandChannel.trySend(RestCommand.Pause(active.exerciseName, remainingSeconds(paused)))
    }

    fun resumeRest() {
        val active = _rest.value ?: return
        if (!active.timer.isPaused) return
        val resumed = active.timer.resumed(clock())
        _rest.value = active.copy(timer = resumed)
        persistRest(active.exerciseStableId, resumed)
        restCommandChannel.trySend(startCommand(active.exerciseName, resumed))
    }

    /** Add/subtract rest time on the running (or paused) countdown, e.g. ±15 s. */
    fun adjustRest(deltaSeconds: Int) {
        val active = _rest.value ?: return
        val adjusted = active.timer.adjusted(clock(), deltaSeconds)
        _rest.value = active.copy(timer = adjusted)
        persistRest(active.exerciseStableId, adjusted)
        val command = if (adjusted.isPaused) {
            RestCommand.Pause(active.exerciseName, remainingSeconds(adjusted))
        } else {
            startCommand(active.exerciseName, adjusted)
        }
        restCommandChannel.trySend(command)
    }

    fun skipRest() = cancelRest()

    private fun cancelRest() {
        if (_rest.value == null) return
        _rest.value = null
        persistRest(null, null)
        restCommandChannel.trySend(RestCommand.Cancel)
    }

    private fun startCommand(exerciseName: String, timer: RestTimer) = RestCommand.Start(
        exerciseName = exerciseName,
        totalSeconds = timer.totalSeconds,
        delayMs = timer.remainingMs(clock()),
        endAtMs = timer.endAtMs,
    )

    private fun remainingSeconds(timer: RestTimer) = ceil(timer.remainingMs(clock()) / 1000.0).toInt()

    /** Persists the rest-timer anchor onto the session so it survives leaving/resuming (spec addendum). */
    private fun persistRest(exerciseStableId: String?, timer: RestTimer?) = mutate { session ->
        session.copy(
            restExerciseStableId = exerciseStableId,
            restTotalSeconds = timer?.totalSeconds,
            restEndAtMs = timer?.endAtMs,
            restPausedRemainingMs = timer?.pausedRemainingMs,
        )
    }

    /**
     * Reconstructs [_rest] from a resumed session's persisted anchor. If the countdown already ran
     * out while the screen was away, the anchor is simply cleared — the background notification (if
     * any) already fired or is about to, independent of this VM instance.
     */
    private fun restoreRestTimer(session: WorkoutSession) {
        val exerciseStableId = session.restExerciseStableId ?: return
        val totalSeconds = session.restTotalSeconds ?: return
        val endAtMs = session.restEndAtMs ?: return
        val timer = RestTimer(totalSeconds, endAtMs, session.restPausedRemainingMs)
        if (!timer.isPaused && timer.isFinished(clock())) {
            persistRest(null, null)
            return
        }
        viewModelScope.launch {
            val exercise = catalog.exercise(exerciseStableId).first()
            _rest.value = ActiveRest(exerciseStableId, exercise?.name ?: exerciseStableId, timer)
        }
    }

    /** Persist a per-exercise rest override (stored on the exercise via [restSeconds]). */
    fun setExerciseRest(exerciseStableId: String, seconds: Int) {
        viewModelScope.launch {
            val exercise = catalog.exercise(exerciseStableId).first() ?: return@launch
            catalog.upsertAll(listOf(exercise.copy(restSeconds = seconds.coerceAtLeast(RestTimer.MIN_REST_SECONDS))))
        }
    }

    // --- lifecycle ---

    fun finish() {
        cancelRest()
        _session.update { it?.copy(isActive = false, endedAtMs = clock()) }
        viewModelScope.launch {
            val session = _session.value
            if (session != null && totalVolumeKg(session) <= 0.0) {
                // An empty workout is discarded, not kept — and there is nothing to sync to a template.
                if (session.id != 0L) sessions.delete(session.id)
                _finished.value = true
                return@launch
            }
            persist()
            // Offer to fold this session's changes back into its template (spec addendum). If nothing
            // structural changed (or it was a free session), finish straight away.
            val merged = mergedTemplateUpdate(_session.value)
            if (merged != null) _pendingTemplateUpdate.value = merged else _finished.value = true
        }
    }

    /**
     * The session's template with this session's changes merged in, or null if there is nothing to
     * update (free session, template gone, or the merge would reproduce the template exactly).
     */
    private suspend fun mergedTemplateUpdate(session: WorkoutSession?): WorkoutTemplate? {
        val stableId = session?.templateStableId ?: return null
        val template = templates.templates().first().firstOrNull { it.stableId == stableId } ?: return null
        return TemplateUpdate.merge(template, session)
    }

    /** Confirms the offered template update: persists the merged template, then finishes. */
    fun confirmTemplateUpdate() {
        val merged = _pendingTemplateUpdate.value
        viewModelScope.launch {
            if (merged != null) templates.save(merged)
            _pendingTemplateUpdate.value = null
            _finished.value = true
        }
    }

    /** Dismisses the offered template update, leaving the template unchanged, then finishes. */
    fun dismissTemplateUpdate() {
        _pendingTemplateUpdate.value = null
        _finished.value = true
    }

    /** Total volume across all exercises — a session with no logged weight/reps is not worth keeping. */
    private suspend fun totalVolumeKg(session: WorkoutSession): Double {
        val byStableId = catalog.exercises().first().associateBy { it.stableId }
        val bodyWeightKg = _bodyWeightKg.value
        return session.exercises.sumOf { se ->
            val type = byStableId[se.exerciseStableId]?.type ?: ExerciseType.WEIGHT_REPS
            WorkoutMetrics.volumeKg(se.sets, type, bodyWeightKg)
        }
    }

    fun discard() {
        cancelRest()
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

    private companion object {
        const val TICK_MS = 250L
    }
}
