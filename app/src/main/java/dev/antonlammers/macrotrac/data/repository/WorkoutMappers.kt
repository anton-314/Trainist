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
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import java.time.LocalDate

/**
 * Pure entity ↔ domain mapping for the workout module — Android-free and unit-tested directly
 * (like `CsvFormat`). List-valued exercise fields are stored newline-joined; child positions are
 * assigned from list order on the way down and used to sort on the way up.
 */
internal object WorkoutMappers {

    private const val LIST_SEPARATOR = "\n"

    private fun List<String>.joinField(): String = joinToString(LIST_SEPARATOR)
    private fun String.splitField(): List<String> =
        if (isEmpty()) emptyList() else split(LIST_SEPARATOR)

    // --- Exercise ---

    fun ExerciseEntity.toDomain(): Exercise = Exercise(
        stableId = stableId,
        name = name,
        type = ExerciseType.parse(type),
        isCustom = isCustom,
        primaryMuscles = primaryMuscles.splitField(),
        secondaryMuscles = secondaryMuscles.splitField(),
        equipment = equipment,
        mechanic = Mechanic.parse(mechanic),
        category = category,
        instructions = instructions.splitField(),
        restSeconds = restSeconds,
    )

    fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
        stableId = stableId,
        name = name,
        type = type.name,
        isCustom = isCustom,
        primaryMuscles = primaryMuscles.joinField(),
        secondaryMuscles = secondaryMuscles.joinField(),
        equipment = equipment,
        mechanic = mechanic?.name,
        category = category,
        instructions = instructions.joinField(),
        restSeconds = restSeconds,
    )

    // --- Template ---

    fun TemplateWithExercises.toDomain(): WorkoutTemplate = WorkoutTemplate(
        id = template.id,
        stableId = template.stableId,
        name = template.name,
        exercises = exercises.sortedBy { it.position }.map {
            TemplateExercise(it.exerciseStableId, it.position, it.targetSets)
        },
    )

    /** [position] is a storage-only ordering concern resolved by the repository, not the domain model. */
    fun WorkoutTemplate.toEntity(position: Int): WorkoutTemplateEntity =
        WorkoutTemplateEntity(id = id, stableId = stableId, name = name, position = position)

    /** Template exercises re-numbered by list order so [position] is always canonical. */
    fun WorkoutTemplate.exerciseEntities(templateId: Long): List<TemplateExerciseEntity> =
        exercises.mapIndexed { index, e ->
            TemplateExerciseEntity(
                templateId = templateId,
                exerciseStableId = e.exerciseStableId,
                position = index,
                targetSets = e.targetSets,
            )
        }

    // --- Session ---

    fun SessionWithExercises.toDomain(): WorkoutSession = WorkoutSession(
        id = session.id,
        stableId = session.stableId,
        date = LocalDate.parse(session.date),
        isActive = session.isActive,
        startedAtMs = session.startedAtMs,
        endedAtMs = session.endedAtMs,
        note = session.note,
        exercises = exercises.sortedBy { it.sessionExercise.position }.map { it.toDomain() },
        templateStableId = session.templateStableId,
        restExerciseStableId = session.restExerciseStableId,
        restTotalSeconds = session.restTotalSeconds,
        restEndAtMs = session.restEndAtMs,
        restPausedRemainingMs = session.restPausedRemainingMs,
    )

    private fun SessionExerciseWithSets.toDomain(): SessionExercise = SessionExercise(
        id = sessionExercise.id,
        exerciseStableId = sessionExercise.exerciseStableId,
        position = sessionExercise.position,
        supersetGroupId = sessionExercise.supersetGroupId,
        sets = sets.sortedBy { it.position }.map {
            SetEntry(
                id = it.id,
                position = it.position,
                weightKg = it.weightKg,
                reps = it.reps,
                type = SetType.parse(it.type),
                completed = it.completed,
            )
        },
    )

    fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
        id = id,
        stableId = stableId,
        date = date.toString(),
        isActive = isActive,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        note = note,
        templateStableId = templateStableId,
        restExerciseStableId = restExerciseStableId,
        restTotalSeconds = restTotalSeconds,
        restEndAtMs = restEndAtMs,
        restPausedRemainingMs = restPausedRemainingMs,
    )

    fun SessionExercise.toEntity(sessionId: Long, position: Int): SessionExerciseEntity =
        SessionExerciseEntity(
            sessionId = sessionId,
            exerciseStableId = exerciseStableId,
            position = position,
            supersetGroupId = supersetGroupId,
        )

    /** Set entries re-numbered by list order so [SetEntry.position] is always canonical. */
    fun SessionExercise.setEntities(sessionExerciseId: Long): List<SetEntryEntity> =
        sets.mapIndexed { index, s ->
            SetEntryEntity(
                sessionExerciseId = sessionExerciseId,
                position = index,
                weightKg = s.weightKg,
                reps = s.reps,
                type = s.type.name,
                completed = s.completed,
            )
        }
}
