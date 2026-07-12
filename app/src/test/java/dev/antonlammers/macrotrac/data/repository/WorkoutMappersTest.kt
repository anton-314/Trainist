package dev.antonlammers.macrotrac.data.repository

import dev.antonlammers.macrotrac.data.local.entity.ExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.SessionExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.SetEntryEntity
import dev.antonlammers.macrotrac.data.local.entity.TemplateExerciseEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutSessionEntity
import dev.antonlammers.macrotrac.data.local.entity.WorkoutTemplateEntity
import dev.antonlammers.macrotrac.data.local.relation.SessionExerciseWithSets
import dev.antonlammers.macrotrac.data.local.relation.SessionWithExercises
import dev.antonlammers.macrotrac.data.local.relation.TemplateWithExercises
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.exerciseEntities
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.setEntities
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.toDomain
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.toEntity
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WorkoutMappersTest {

    @Test
    fun `exercise round-trips through entity preserving lists and nullable fields`() {
        val exercise = Exercise(
            stableId = "Barbell_Bench_Press",
            name = "Bench Press",
            type = ExerciseType.WEIGHT_REPS,
            isCustom = false,
            primaryMuscles = listOf("chest"),
            secondaryMuscles = listOf("shoulders", "triceps"),
            equipment = "barbell",
            mechanic = Mechanic.COMPOUND,
            category = "strength",
            instructions = listOf("Lie on the bench.", "Press up, then lower under control."),
            restSeconds = 120,
        )

        assertEquals(exercise, exercise.toEntity().toDomain())
    }

    @Test
    fun `empty muscle and instruction lists survive as empty, not a blank element`() {
        val custom = Exercise(
            stableId = "custom-uuid",
            name = "Meine Übung",
            type = ExerciseType.BODYWEIGHT,
            isCustom = true,
        )
        val entity = custom.toEntity()

        assertEquals("", entity.primaryMuscles)
        assertEquals("", entity.instructions)
        assertNull(entity.mechanic)
        assertNull(entity.restSeconds)

        val back = entity.toDomain()
        assertEquals(emptyList<String>(), back.primaryMuscles)
        assertEquals(emptyList<String>(), back.instructions)
        assertEquals(custom, back)
    }

    @Test
    fun `template maps sorted by position and re-numbers entities by list order`() {
        val relation = TemplateWithExercises(
            template = WorkoutTemplateEntity(id = 5, stableId = "tpl", name = "Push Day"),
            // Deliberately out of order — mapping must sort by position.
            exercises = listOf(
                TemplateExerciseEntity(id = 2, templateId = 5, exerciseStableId = "b", position = 1, targetSets = 4),
                TemplateExerciseEntity(id = 1, templateId = 5, exerciseStableId = "a", position = 0, targetSets = 3),
            ),
        )

        val domain = relation.toDomain()
        assertEquals(5L, domain.id)
        assertEquals(listOf("a", "b"), domain.exercises.map { it.exerciseStableId })
        assertEquals(listOf(0, 1), domain.exercises.map { it.position })

        // Re-numbering ignores any stale incoming position and uses list order.
        val template = WorkoutTemplate(
            id = 5, stableId = "tpl", name = "Push Day",
            exercises = listOf(
                TemplateExercise("a", position = 99, targetSets = 3),
                TemplateExercise("b", position = 99, targetSets = 4),
            ),
        )
        val entities = template.exerciseEntities(templateId = 5)
        assertEquals(listOf(0, 1), entities.map { it.position })
        assertEquals(listOf("a", "b"), entities.map { it.exerciseStableId })
        assertEquals(listOf(5L, 5L), entities.map { it.templateId })
    }

    @Test
    fun `template entity carries the manual drag-order position, not the domain model`() {
        val template = WorkoutTemplate(id = 5, stableId = "tpl", name = "Push Day")

        assertEquals(3, template.toEntity(position = 3).position)
        assertEquals(0, template.toEntity(position = 0).position)
    }

    @Test
    fun `session maps nested graph sorted by position with parsed enums`() {
        val relation = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 7, stableId = "sess", date = "2026-07-10", isActive = false,
                startedAtMs = 1_000, endedAtMs = 5_000, note = "leg day",
            ),
            exercises = listOf(
                SessionExerciseWithSets(
                    sessionExercise = SessionExerciseEntity(id = 20, sessionId = 7, exerciseStableId = "squat", position = 0, supersetGroupId = null),
                    sets = listOf(
                        SetEntryEntity(id = 31, sessionExerciseId = 20, position = 1, weightKg = 100.0, reps = 5, type = "NORMAL", completed = true),
                        SetEntryEntity(id = 30, sessionExerciseId = 20, position = 0, weightKg = 60.0, reps = 8, type = "WARMUP", completed = true),
                    ),
                ),
            ),
        )

        val domain = relation.toDomain()
        assertEquals(LocalDate.of(2026, 7, 10), domain.date)
        assertEquals(5_000L - 1_000L, domain.durationMs)
        val sets = domain.exercises.single().sets
        assertEquals(listOf(0, 1), sets.map { it.position })
        assertEquals(SetType.WARMUP, sets.first().type)
        assertEquals(60.0, sets.first().weightKg, 0.0)
    }

    @Test
    fun `session entity mapping numbers exercises and sets by list order`() {
        val exercise = SessionExercise(
            exerciseStableId = "squat", position = 42,
            sets = listOf(
                SetEntry(position = 9, weightKg = 60.0, reps = 8, type = SetType.WARMUP),
                SetEntry(position = 9, weightKg = 100.0, reps = 5),
            ),
        )
        val seEntity = exercise.toEntity(sessionId = 7, position = 0)
        assertEquals(7L, seEntity.sessionId)
        assertEquals(0, seEntity.position)

        val setEntities = exercise.setEntities(sessionExerciseId = 20)
        assertEquals(listOf(0, 1), setEntities.map { it.position })
        assertEquals(listOf("WARMUP", "NORMAL"), setEntities.map { it.type })
        assertEquals(listOf(20L, 20L), setEntities.map { it.sessionExerciseId })
    }

    @Test
    fun `session round-trips a persisted rest-timer anchor`() {
        val session = WorkoutSession(
            id = 7, stableId = "sess", date = LocalDate.of(2026, 7, 10), isActive = true,
            startedAtMs = 1_000,
            restExerciseStableId = "bench", restTotalSeconds = 180, restEndAtMs = 181_000, restPausedRemainingMs = 45_000,
        )
        val entity = session.toEntity()
        assertEquals("bench", entity.restExerciseStableId)
        assertEquals(180, entity.restTotalSeconds)
        assertEquals(181_000L, entity.restEndAtMs)
        assertEquals(45_000L, entity.restPausedRemainingMs)

        val relation = SessionWithExercises(session = entity, exercises = emptyList())
        val back = relation.toDomain()
        assertEquals("bench", back.restExerciseStableId)
        assertEquals(180, back.restTotalSeconds)
        assertEquals(181_000L, back.restEndAtMs)
        assertEquals(45_000L, back.restPausedRemainingMs)
    }

    @Test
    fun `session round-trips the template it was started from, or null for a free workout`() {
        val fromTemplate = WorkoutSession(
            stableId = "sess", date = LocalDate.of(2026, 7, 10), isActive = true,
            startedAtMs = 1_000, templateStableId = "tpl-push",
        )
        val entity = fromTemplate.toEntity()
        assertEquals("tpl-push", entity.templateStableId)
        val relation = SessionWithExercises(session = entity, exercises = emptyList())
        assertEquals("tpl-push", relation.toDomain().templateStableId)

        val free = fromTemplate.copy(templateStableId = null)
        assertNull(free.toEntity().templateStableId)
    }

    @Test
    fun `active session maps with null ended and note`() {
        val relation = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1, stableId = "s", date = "2026-07-10", isActive = true,
                startedAtMs = 1_000, endedAtMs = null, note = null,
            ),
            exercises = emptyList(),
        )
        val domain: WorkoutSession = relation.toDomain()
        assertNull(domain.endedAtMs)
        assertNull(domain.note)
        assertNull(domain.durationMs)
        assertEquals(true, domain.isActive)
    }
}
