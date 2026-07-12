package dev.antonlammers.macrotrac.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A training session. [date] is an ISO-8601 string like the other date columns. [isActive] is
 * indexed so the single in-progress session is cheap to look up; at most one row has isActive = 1
 * (enforced in app logic). [endedAtMs] is null while active.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["stableId"], unique = true), Index("isActive")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String,
    val date: String,
    val isActive: Boolean,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val note: String?,
    /** The template this session was started from, if any — drives "last used" per template. */
    val templateStableId: String? = null,
    val restExerciseStableId: String? = null,
    val restTotalSeconds: Int? = null,
    val restEndAtMs: Long? = null,
    val restPausedRemainingMs: Long? = null,
)
