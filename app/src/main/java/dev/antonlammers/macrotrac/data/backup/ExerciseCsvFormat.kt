package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic

/**
 * Pure Kotlin CSV serialisation for [Exercise] — no Android dependencies, fully unit-testable.
 *
 * Only **custom** exercises are exported (catalog entries are reproduced by the bundled snapshot
 * seeding on every install, so shipping all ~800 of them in every backup would be wasteful). The
 * `is_custom` column is written all the same so the format stays self-describing and can carry
 * catalog entries too if that ever becomes necessary.
 *
 * List fields (muscles, instructions) hold multiple values in one CSV cell, joined by
 * [LIST_SEPARATOR] (`|`) — a single line per row, so the line-based ZIP/CSV reader keeps working
 * (an embedded newline would break it). The whole cell is still CSV-escaped.
 */
object ExerciseCsvFormat {

    private const val STABLE_ID = "exercise_stable_id"
    private const val NAME = "name"
    private const val TYPE = "type"
    const val IS_CUSTOM = "is_custom"
    private const val PRIMARY_MUSCLES = "primary_muscles"
    private const val SECONDARY_MUSCLES = "secondary_muscles"
    private const val EQUIPMENT = "equipment"
    private const val MECHANIC = "mechanic"
    private const val CATEGORY = "category"
    private const val INSTRUCTIONS = "instructions"
    private const val REST_SECONDS = "rest_seconds"

    private const val LIST_SEPARATOR = "|"

    val HEADER: String = listOf(
        STABLE_ID, NAME, TYPE, IS_CUSTOM, PRIMARY_MUSCLES, SECONDARY_MUSCLES,
        EQUIPMENT, MECHANIC, CATEGORY, INSTRUCTIONS, REST_SECONDS,
    ).joinToString(",")

    fun toRow(exercise: Exercise): String = listOf(
        exercise.stableId.escapeCsv(),
        exercise.name.escapeCsv(),
        exercise.type.name,
        exercise.isCustom,
        exercise.primaryMuscles.joinToString(LIST_SEPARATOR).escapeCsv(),
        exercise.secondaryMuscles.joinToString(LIST_SEPARATOR).escapeCsv(),
        exercise.equipment?.escapeCsv() ?: "",
        exercise.mechanic?.name ?: "",
        exercise.category?.escapeCsv() ?: "",
        exercise.instructions.joinToString(LIST_SEPARATOR).escapeCsv(),
        exercise.restSeconds ?: "",
    ).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): Exercise? {
        val cols = CsvFormat.parseLine(row)
        val stableId = cols.csvStr(headers, STABLE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val name = cols.csvStr(headers, NAME)?.takeIf { it.isNotBlank() } ?: return null
        return Exercise(
            stableId = stableId,
            name = name,
            type = ExerciseType.parse(cols.csvStr(headers, TYPE)),
            // Missing column → treat as custom: exported rows are customs, and a referenced exercise
            // worth carrying in a backup is one that isn't in the bundled catalog.
            isCustom = cols.csvBool(headers, IS_CUSTOM) ?: true,
            primaryMuscles = cols.csvList(headers, PRIMARY_MUSCLES),
            secondaryMuscles = cols.csvList(headers, SECONDARY_MUSCLES),
            equipment = cols.csvStr(headers, EQUIPMENT)?.takeIf { it.isNotBlank() },
            mechanic = Mechanic.parse(cols.csvStr(headers, MECHANIC)),
            category = cols.csvStr(headers, CATEGORY)?.takeIf { it.isNotBlank() },
            instructions = cols.csvList(headers, INSTRUCTIONS),
            restSeconds = cols.csvInt(headers, REST_SECONDS),
        )
    }

    private fun List<String>.csvList(headers: Map<String, Int>, col: String): List<String> =
        csvStr(headers, col)?.split(LIST_SEPARATOR)?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()
}
