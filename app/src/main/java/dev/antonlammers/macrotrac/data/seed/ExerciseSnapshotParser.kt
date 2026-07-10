package dev.antonlammers.macrotrac.data.seed

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic

/**
 * Parses the bundled `free-exercise-db` snapshot — a JSON array of catalog exercises — into domain
 * [Exercise]s. Pure JVM (Moshi has no Android deps) so it is unit-testable directly, like `CsvFormat`.
 *
 * Catalog entries are never custom. The source carries no explicit set type, so [ExerciseType] is
 * derived from the equipment (`"body only"` → [ExerciseType.BODYWEIGHT], everything else →
 * [ExerciseType.WEIGHT_REPS]). Only the fields we actually use are read; images/force/level in the
 * upstream data are ignored. Missing/unknown values fall back to sensible defaults.
 */
object ExerciseSnapshotParser {

    private val listType = Types.newParameterizedType(List::class.java, ExerciseDto::class.java)
    private val adapter = Moshi.Builder().build().adapter<List<ExerciseDto>>(listType)

    private const val BODYWEIGHT_EQUIPMENT = "body only"

    fun parse(json: String): List<Exercise> =
        (adapter.fromJson(json) ?: emptyList()).map { it.toExercise() }

    private fun ExerciseDto.toExercise(): Exercise = Exercise(
        stableId = id,
        name = name,
        type = if (equipment?.trim()?.lowercase() == BODYWEIGHT_EQUIPMENT) {
            ExerciseType.BODYWEIGHT
        } else {
            ExerciseType.WEIGHT_REPS
        },
        isCustom = false,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        equipment = equipment,
        mechanic = Mechanic.parse(mechanic),
        category = category,
        instructions = instructions,
        restSeconds = null,
    )
}

@JsonClass(generateAdapter = true)
internal data class ExerciseDto(
    val id: String,
    val name: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String? = null,
    val mechanic: String? = null,
    val category: String? = null,
    val instructions: List<String> = emptyList(),
)
