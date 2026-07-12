package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import java.time.LocalDate

/**
 * Pure Kotlin CSV serialisation for the [WorkoutSession] header row. Its exercises live in
 * [SessionExerciseCsvFormat] and their sets in [SetEntryCsvFormat], all linked back by the session's
 * [WorkoutSession.stableId]. Row ids are never exported; import mints fresh ones and reconnects the
 * graph by stable keys / positions.
 */
object WorkoutSessionCsvFormat {

    const val STABLE_ID = "session_stable_id"
    private const val DATE = "date"
    const val IS_ACTIVE = "is_active"
    private const val STARTED_AT_MS = "started_at_ms"
    private const val ENDED_AT_MS = "ended_at_ms"
    private const val NOTE = "note"
    private const val TEMPLATE_STABLE_ID = "template_stable_id"

    val HEADER: String =
        listOf(STABLE_ID, DATE, IS_ACTIVE, STARTED_AT_MS, ENDED_AT_MS, NOTE, TEMPLATE_STABLE_ID).joinToString(",")

    fun toRow(session: WorkoutSession): String = listOf(
        session.stableId.escapeCsv(),
        session.date.toString(),
        session.isActive,
        session.startedAtMs,
        session.endedAtMs ?: "",
        session.note?.escapeCsv() ?: "",
        session.templateStableId?.escapeCsv() ?: "",
    ).joinToString(",")

    /** Parses a header row into a session with an empty exercise list (attached on assembly). */
    fun fromRow(row: String, headers: Map<String, Int>): WorkoutSession? {
        val cols = CsvFormat.parseLine(row)
        val stableId = cols.csvStr(headers, STABLE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val date = cols.csvStr(headers, DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val startedAtMs = cols.csvLong(headers, STARTED_AT_MS) ?: return null
        return WorkoutSession(
            stableId = stableId,
            date = date,
            isActive = cols.csvBool(headers, IS_ACTIVE) ?: false,
            startedAtMs = startedAtMs,
            endedAtMs = cols.csvLong(headers, ENDED_AT_MS),
            note = cols.csvStr(headers, NOTE)?.takeIf { it.isNotBlank() },
            // Missing on older backups (no such column yet) — the session simply has no known template.
            templateStableId = cols.csvStr(headers, TEMPLATE_STABLE_ID)?.takeIf { it.isNotBlank() },
            exercises = emptyList(),
        )
    }
}

/**
 * Pure Kotlin CSV serialisation for a single [SessionExercise]. Each row carries the owning
 * session's [WorkoutSessionCsvFormat.STABLE_ID] plus the exercise's own stable key and its
 * [position] (the key its sets reference — an exercise can appear more than once in a session).
 */
object SessionExerciseCsvFormat {

    const val SESSION_STABLE_ID = "session_stable_id"
    const val EXERCISE_STABLE_ID = "exercise_stable_id"
    const val POSITION = "position"
    const val SUPERSET_GROUP_ID = "superset_group_id"

    val HEADER: String =
        listOf(SESSION_STABLE_ID, EXERCISE_STABLE_ID, POSITION, SUPERSET_GROUP_ID).joinToString(",")

    fun toRow(sessionStableId: String, exercise: SessionExercise): String = listOf(
        sessionStableId.escapeCsv(),
        exercise.exerciseStableId.escapeCsv(),
        exercise.position,
        exercise.supersetGroupId ?: "",
    ).joinToString(",")

    /** A parsed exercise (sets attached on assembly) with the stable id of its owning session. */
    data class Row(val sessionStableId: String, val exercise: SessionExercise)

    fun fromRow(row: String, headers: Map<String, Int>): Row? {
        val cols = CsvFormat.parseLine(row)
        val sessionStableId = cols.csvStr(headers, SESSION_STABLE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val exerciseStableId = cols.csvStr(headers, EXERCISE_STABLE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val position = cols.csvInt(headers, POSITION) ?: return null
        return Row(
            sessionStableId,
            SessionExercise(
                exerciseStableId = exerciseStableId,
                position = position,
                supersetGroupId = cols.csvInt(headers, SUPERSET_GROUP_ID),
                sets = emptyList(),
            ),
        )
    }
}

/**
 * Pure Kotlin CSV serialisation for a single [SetEntry]. Its parent session exercise is identified
 * by the pair (owning session's stable id, that exercise's position within the session) — positions
 * are unique per session, so this reconnects sets to the right exercise even when one exercise
 * appears twice.
 */
object SetEntryCsvFormat {

    const val SESSION_STABLE_ID = "session_stable_id"
    const val EXERCISE_POSITION = "exercise_position"
    const val POSITION = "position"
    private const val WEIGHT_KG = "weight_kg"
    private const val REPS = "reps"
    private const val TYPE = "type"
    private const val COMPLETED = "completed"

    val HEADER: String = listOf(
        SESSION_STABLE_ID, EXERCISE_POSITION, POSITION, WEIGHT_KG, REPS, TYPE, COMPLETED,
    ).joinToString(",")

    fun toRow(sessionStableId: String, exercisePosition: Int, set: SetEntry): String = listOf(
        sessionStableId.escapeCsv(),
        exercisePosition,
        set.position,
        set.weightKg,
        set.reps,
        set.type.name,
        set.completed,
    ).joinToString(",")

    /** A parsed set with the keys of its owning (session, session-exercise-position). */
    data class Row(val sessionStableId: String, val exercisePosition: Int, val set: SetEntry)

    fun fromRow(row: String, headers: Map<String, Int>): Row? {
        val cols = CsvFormat.parseLine(row)
        val sessionStableId = cols.csvStr(headers, SESSION_STABLE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val exercisePosition = cols.csvInt(headers, EXERCISE_POSITION) ?: return null
        val position = cols.csvInt(headers, POSITION) ?: return null
        return Row(
            sessionStableId,
            exercisePosition,
            SetEntry(
                position = position,
                weightKg = cols.csvDbl(headers, WEIGHT_KG) ?: 0.0,
                reps = cols.csvInt(headers, REPS) ?: 0,
                type = SetType.parse(cols.csvStr(headers, TYPE)),
                completed = cols.csvBool(headers, COMPLETED) ?: false,
            ),
        )
    }
}
