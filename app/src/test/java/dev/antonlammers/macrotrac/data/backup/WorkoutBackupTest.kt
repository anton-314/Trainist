package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure reassembly + format round-trips for the training backup sections (no Android, no I/O). */
class WorkoutBackupTest {

    @Test
    fun `exercise row survives a round-trip including comma-bearing free text`() {
        val exercise = Exercise(
            stableId = "x1",
            name = "Bulgarian Split Squat, hoch",
            type = ExerciseType.BODYWEIGHT,
            isCustom = true,
            primaryMuscles = listOf("Quads", "Glutes"),
            instructions = listOf("Rechts, dann links", "Kontrolliert ablassen"),
            equipment = "dumbbell",
            restSeconds = 75,
        )
        val headers = CsvFormat.parseHeaders(ExerciseCsvFormat.HEADER)

        val parsed = ExerciseCsvFormat.fromRow(ExerciseCsvFormat.toRow(exercise), headers)

        assertEquals(exercise, parsed)
    }

    @Test
    fun `empty sections reassemble to empty lists`() {
        assertTrue(WorkoutBackup.parseExercises(null).isEmpty())
        assertTrue(WorkoutBackup.parseExercises(listOf(ExerciseCsvFormat.HEADER)).isEmpty())
        assertTrue(WorkoutBackup.assembleTemplates(null, null).isEmpty())
        assertTrue(WorkoutBackup.assembleSessions(null, null, null).isEmpty())
    }

    @Test
    fun `assembleSessions orders exercises and sets by position regardless of row order`() {
        val session = WorkoutSession(
            stableId = "s1",
            date = LocalDate.parse("2026-07-01"),
            isActive = false,
            startedAtMs = 1000,
            exercises = listOf(
                SessionExercise(
                    exerciseStableId = "bench",
                    position = 0,
                    sets = listOf(
                        SetEntry(position = 0, weightKg = 60.0, reps = 10, type = SetType.NORMAL),
                        SetEntry(position = 1, weightKg = 65.0, reps = 8, type = SetType.NORMAL),
                    ),
                ),
                SessionExercise(
                    exerciseStableId = "row",
                    position = 1,
                    sets = listOf(SetEntry(position = 0, weightKg = 50.0, reps = 12, type = SetType.NORMAL)),
                ),
            ),
        )

        // Deliberately scramble the row order of every section before reassembly.
        val sessionLines = listOf(WorkoutSessionCsvFormat.HEADER, WorkoutSessionCsvFormat.toRow(session))
        val exerciseLines = listOf(SessionExerciseCsvFormat.HEADER) +
            session.exercises.reversed().map { SessionExerciseCsvFormat.toRow(session.stableId, it) }
        val setLines = listOf(SetEntryCsvFormat.HEADER) +
            session.exercises.flatMap { e -> e.sets.reversed().map { SetEntryCsvFormat.toRow(session.stableId, e.position, it) } }

        val rebuilt = WorkoutBackup.assembleSessions(sessionLines, exerciseLines, setLines).single()

        assertEquals(session, rebuilt.copy(id = 0))
    }

    @Test
    fun `sets are keyed by exercise position so a duplicated exercise keeps its own sets`() {
        // The same exercise appears twice in one session at different positions.
        val session = WorkoutSession(
            stableId = "s2",
            date = LocalDate.parse("2026-07-02"),
            isActive = true,
            startedAtMs = 2000,
            exercises = listOf(
                SessionExercise(
                    exerciseStableId = "curl",
                    position = 0,
                    sets = listOf(SetEntry(position = 0, weightKg = 20.0, reps = 10, type = SetType.NORMAL)),
                ),
                SessionExercise(
                    exerciseStableId = "curl",
                    position = 1,
                    sets = listOf(SetEntry(position = 0, weightKg = 15.0, reps = 15, type = SetType.DROP)),
                ),
            ),
        )
        val sessionLines = listOf(WorkoutSessionCsvFormat.HEADER, WorkoutSessionCsvFormat.toRow(session))
        val exerciseLines = listOf(SessionExerciseCsvFormat.HEADER) +
            session.exercises.map { SessionExerciseCsvFormat.toRow(session.stableId, it) }
        val setLines = listOf(SetEntryCsvFormat.HEADER) +
            session.exercises.flatMap { e -> e.sets.map { SetEntryCsvFormat.toRow(session.stableId, e.position, it) } }

        val rebuilt = WorkoutBackup.assembleSessions(sessionLines, exerciseLines, setLines).single()

        assertEquals(20.0, rebuilt.exercises[0].sets.single().weightKg, 0.001)
        assertEquals(SetType.DROP, rebuilt.exercises[1].sets.single().type)
        assertTrue(rebuilt.isActive)
    }
}
