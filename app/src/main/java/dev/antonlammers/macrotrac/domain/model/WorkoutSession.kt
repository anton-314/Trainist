package dev.antonlammers.macrotrac.domain.model

import java.time.LocalDate

/**
 * A single training session. [isActive] marks the one in-progress session (at most one active at a
 * time); it is persisted continuously so it survives app death and can be resumed. [endedAtMs] is
 * null while active. [stableId] is the backup-stable key (UUID).
 */
data class WorkoutSession(
    val id: Long = 0,
    val stableId: String,
    val date: LocalDate,
    val isActive: Boolean,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val note: String? = null,
    val exercises: List<SessionExercise> = emptyList(),
    /** The template this session was started from, if any — drives "last used" per template. */
    val templateStableId: String? = null,
    /**
     * Anchor of an in-progress rest timer, persisted so it survives leaving and resuming the
     * session (all null when no rest is running). [restEndAtMs]/[restPausedRemainingMs] mirror
     * [dev.antonlammers.macrotrac.domain.RestTimer]'s own fields; [restEndAtMs] is kept even while
     * paused (ignored until resumed).
     */
    val restExerciseStableId: String? = null,
    val restTotalSeconds: Int? = null,
    val restEndAtMs: Long? = null,
    val restPausedRemainingMs: Long? = null,
) {
    /** Elapsed time once finished; null while the session is still active. */
    val durationMs: Long? get() = endedAtMs?.let { it - startedAtMs }
}

/**
 * One exercise performed within a [WorkoutSession], referenced by [exerciseStableId]. [position] is
 * the order; [supersetGroupId] is reserved for future superset grouping (always null in v1).
 */
data class SessionExercise(
    val id: Long = 0,
    val exerciseStableId: String,
    val position: Int,
    val supersetGroupId: Int? = null,
    val sets: List<SetEntry> = emptyList(),
)

/** A single logged set: effective inputs plus its [type] and whether it has been checked off. */
data class SetEntry(
    val id: Long = 0,
    val position: Int,
    val weightKg: Double,
    val reps: Int,
    val type: SetType = SetType.NORMAL,
    val completed: Boolean = false,
)
