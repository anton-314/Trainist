package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate

/**
 * Pure Kotlin CSV serialisation for the [WorkoutTemplate] header row (its exercise slots live in
 * [TemplateExerciseCsvFormat], linked back by [WorkoutTemplate.stableId]). The row id is never
 * exported — cross-device import mints fresh row ids and reconnects the slots by the stable key.
 */
object WorkoutTemplateCsvFormat {

    const val STABLE_ID = "template_stable_id"
    const val NAME = "template_name"

    val HEADER: String = listOf(STABLE_ID, NAME).joinToString(",")

    fun toRow(template: WorkoutTemplate): String = listOf(
        template.stableId.escapeCsv(),
        template.name.escapeCsv(),
    ).joinToString(",")

    /** Parses a header row into a template with an empty exercise list (slots attached on assembly). */
    fun fromRow(row: String, headers: Map<String, Int>): WorkoutTemplate? {
        val cols = CsvFormat.parseLine(row)
        val stableId = cols.csvStr(headers, STABLE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val name = cols.csvStr(headers, NAME)?.takeIf { it.isNotBlank() } ?: return null
        return WorkoutTemplate(stableId = stableId, name = name, exercises = emptyList())
    }
}

/**
 * Pure Kotlin CSV serialisation for a single [TemplateExercise] slot. Each row carries the owning
 * template's [WorkoutTemplateCsvFormat.STABLE_ID] as a foreign key so the slots can be re-grouped
 * under their template on import, plus the exercise's own stable key.
 */
object TemplateExerciseCsvFormat {

    const val TEMPLATE_STABLE_ID = "template_stable_id"
    const val EXERCISE_STABLE_ID = "exercise_stable_id"
    const val POSITION = "position"
    const val TARGET_SETS = "target_sets"

    val HEADER: String =
        listOf(TEMPLATE_STABLE_ID, EXERCISE_STABLE_ID, POSITION, TARGET_SETS).joinToString(",")

    fun toRow(templateStableId: String, exercise: TemplateExercise): String = listOf(
        templateStableId.escapeCsv(),
        exercise.exerciseStableId.escapeCsv(),
        exercise.position,
        exercise.targetSets,
    ).joinToString(",")

    /** A parsed slot together with the stable id of the template it belongs to. */
    data class Row(val templateStableId: String, val exercise: TemplateExercise)

    fun fromRow(row: String, headers: Map<String, Int>): Row? {
        val cols = CsvFormat.parseLine(row)
        val templateStableId = cols.csvStr(headers, TEMPLATE_STABLE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val exerciseStableId = cols.csvStr(headers, EXERCISE_STABLE_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val position = cols.csvInt(headers, POSITION) ?: return null
        val targetSets = cols.csvInt(headers, TARGET_SETS) ?: 1
        return Row(
            templateStableId,
            TemplateExercise(
                exerciseStableId = exerciseStableId,
                position = position,
                targetSets = targetSets,
            ),
        )
    }
}
