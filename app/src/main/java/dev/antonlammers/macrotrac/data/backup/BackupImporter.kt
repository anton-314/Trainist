package dev.antonlammers.macrotrac.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import dev.antonlammers.macrotrac.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutSessionRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

internal enum class CsvType {
    FOOD_ENTRIES, WEIGHT_ENTRIES, DAILY_GOAL, CUSTOM_FOODS,
    EXERCISES, WORKOUT_TEMPLATES, TEMPLATE_EXERCISES,
    WORKOUT_SESSIONS, SESSION_EXERCISES, SET_ENTRIES,
    UNKNOWN,
}

internal fun detectCsvType(headers: Map<String, Int>): CsvType = when {
    // Training sections are matched first by their own unique columns: set_entries carries a
    // `weight_kg` column too, so it must win over the weight-entries rule below.
    ExerciseCsvFormat.IS_CUSTOM in headers -> CsvType.EXERCISES
    WorkoutTemplateCsvFormat.NAME in headers -> CsvType.WORKOUT_TEMPLATES
    TemplateExerciseCsvFormat.TARGET_SETS in headers -> CsvType.TEMPLATE_EXERCISES
    WorkoutSessionCsvFormat.IS_ACTIVE in headers -> CsvType.WORKOUT_SESSIONS
    SessionExerciseCsvFormat.SUPERSET_GROUP_ID in headers -> CsvType.SESSION_EXERCISES
    SetEntryCsvFormat.EXERCISE_POSITION in headers -> CsvType.SET_ENTRIES
    CsvColumns.FOOD_NAME in headers -> CsvType.FOOD_ENTRIES
    "weight_kg" in headers -> CsvType.WEIGHT_ENTRIES
    "kcal_per_100g" in headers -> CsvType.CUSTOM_FOODS
    "kcal" in headers -> CsvType.DAILY_GOAL
    else -> CsvType.UNKNOWN
}

/** Every repository a backup import writes to — grouped so the pure import functions stay readable. */
internal class ImportTargets(
    val foodEntryRepository: FoodEntryRepository,
    val weightRepository: WeightRepository,
    val goalRepository: GoalRepository,
    val customFoodRepository: CustomFoodRepository,
    val exerciseCatalogRepository: ExerciseCatalogRepository,
    val workoutTemplateRepository: WorkoutTemplateRepository,
    val workoutSessionRepository: WorkoutSessionRepository,
)

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
    private val customFoodRepository: CustomFoodRepository,
    private val exerciseCatalogRepository: ExerciseCatalogRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
) {
    data class Result(
        val foodImported: Int = 0,
        val foodSkipped: Int = 0,
        val weightImported: Int = 0,
        val goalRestored: Boolean = false,
        val customFoodsImported: Int = 0,
        val exercisesImported: Int = 0,
        val templatesImported: Int = 0,
        val sessionsImported: Int = 0,
    )

    private val targets = ImportTargets(
        foodEntryRepository, weightRepository, goalRepository, customFoodRepository,
        exerciseCatalogRepository, workoutTemplateRepository, workoutSessionRepository,
    )

    suspend fun import(uri: Uri): Result =
        if (isZip(uri)) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                importZipEntries(input, targets)
            } ?: Result()
        } else {
            val lines = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()
                ?.filter { it.isNotBlank() }
                ?: return Result()
            importCsvLines(lines, targets)
        }

    private fun isZip(uri: Uri): Boolean =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(2)
            stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } ?: false
}

/**
 * Reads a backup ZIP into memory, then dispatches each known section to its parser and repository.
 * Pure with respect to Android (takes a plain [InputStream]) so it is directly unit-testable.
 *
 * Unknown or missing entries are ignored, so backups from older app versions — including ones with
 * no training data at all — still import: absent training sections simply reassemble to nothing.
 * Training data is relational, so its six sections are collected and reassembled together (their
 * foreign keys are stable string keys, reconnected on import) rather than saved row by row.
 */
internal suspend fun importZipEntries(input: InputStream, targets: ImportTargets): BackupImporter.Result {
    val sections = HashMap<String, List<String>>()
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val lines = zip.readBytes().toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
            sections[entry.name] = lines
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    var foodImported = 0
    var foodSkipped = 0
    sections["food_entries.csv"]?.let { lines ->
        if (lines.size > 1) {
            val headers = CsvFormat.parseHeaders(lines.first())
            lines.drop(1).forEach { line ->
                val e = runCatching { CsvFormat.fromRow(line, headers) }.getOrNull()
                if (e != null) { targets.foodEntryRepository.add(e); foodImported++ } else foodSkipped++
            }
        }
    }

    var weightImported = 0
    sections["weight_entries.csv"]?.let { lines ->
        if (lines.size > 1) {
            val headers = CsvFormat.parseHeaders(lines.first())
            lines.drop(1).forEach { line ->
                val e = runCatching { WeightCsvFormat.fromRow(line, headers) }.getOrNull()
                if (e != null) { targets.weightRepository.save(e); weightImported++ }
            }
        }
    }

    var goalRestored = false
    sections["daily_goal.csv"]?.let { lines ->
        if (lines.size > 1) {
            val headers = CsvFormat.parseHeaders(lines.first())
            val goal = runCatching { GoalCsvFormat.fromRow(lines[1], headers) }.getOrNull()
            if (goal != null) { targets.goalRepository.save(goal); goalRestored = true }
        }
    }

    var customFoodsImported = 0
    sections["custom_foods.csv"]?.let { lines ->
        if (lines.size > 1) {
            val headers = CsvFormat.parseHeaders(lines.first())
            lines.drop(1).forEach { line ->
                val f = runCatching { CustomFoodCsvFormat.fromRow(line, headers) }.getOrNull()
                if (f != null) { targets.customFoodRepository.save(f); customFoodsImported++ }
            }
        }
    }

    val (exercisesImported, templatesImported, sessionsImported) = importWorkoutSections(
        exerciseLines = sections[WorkoutBackupEntries.EXERCISES],
        templateLines = sections[WorkoutBackupEntries.WORKOUT_TEMPLATES],
        templateExerciseLines = sections[WorkoutBackupEntries.TEMPLATE_EXERCISES],
        sessionLines = sections[WorkoutBackupEntries.WORKOUT_SESSIONS],
        sessionExerciseLines = sections[WorkoutBackupEntries.SESSION_EXERCISES],
        setLines = sections[WorkoutBackupEntries.SET_ENTRIES],
        targets = targets,
    )

    return BackupImporter.Result(
        foodImported = foodImported,
        foodSkipped = foodSkipped,
        weightImported = weightImported,
        goalRestored = goalRestored,
        customFoodsImported = customFoodsImported,
        exercisesImported = exercisesImported,
        templatesImported = templatesImported,
        sessionsImported = sessionsImported,
    )
}

/**
 * Reassembles the relational training graph from its flat sections and writes it. Missing sections
 * yield empty lists (no error). Custom exercises are upserted first so any template/session
 * referencing them resolves; catalog references resolve against the target's seeded catalog by their
 * stable id. Returns (exercises, templates, sessions) imported.
 */
private suspend fun importWorkoutSections(
    exerciseLines: List<String>?,
    templateLines: List<String>?,
    templateExerciseLines: List<String>?,
    sessionLines: List<String>?,
    sessionExerciseLines: List<String>?,
    setLines: List<String>?,
    targets: ImportTargets,
): Triple<Int, Int, Int> {
    val exercises = WorkoutBackup.parseExercises(exerciseLines)
    if (exercises.isNotEmpty()) targets.exerciseCatalogRepository.upsertAll(exercises)

    val templates = WorkoutBackup.assembleTemplates(templateLines, templateExerciseLines)
    templates.forEach { targets.workoutTemplateRepository.save(it.copy(id = 0)) }

    val sessions = WorkoutBackup.assembleSessions(sessionLines, sessionExerciseLines, setLines)
    sessions.forEach { targets.workoutSessionRepository.save(it.copy(id = 0)) }

    return Triple(exercises.size, templates.size, sessions.size)
}

/**
 * Imports a single CSV (already split into non-blank lines), routing by detected type.
 * Pure with respect to Android so it is directly unit-testable.
 *
 * Relational training sections (templates/sessions and their children) only reassemble as a whole
 * ZIP and so are no-ops here; the standalone exercise catalog CSV does import on its own.
 */
internal suspend fun importCsvLines(lines: List<String>, targets: ImportTargets): BackupImporter.Result {
    if (lines.size < 2) return BackupImporter.Result()

    val headers = CsvFormat.parseHeaders(lines.first())
    return when (detectCsvType(headers)) {
        CsvType.FOOD_ENTRIES -> {
            var imported = 0; var skipped = 0
            lines.drop(1).forEach { line ->
                val e = runCatching { CsvFormat.fromRow(line, headers) }.getOrNull()
                if (e != null) { targets.foodEntryRepository.add(e); imported++ } else skipped++
            }
            BackupImporter.Result(foodImported = imported, foodSkipped = skipped)
        }
        CsvType.WEIGHT_ENTRIES -> {
            var imported = 0
            lines.drop(1).forEach { line ->
                val e = runCatching { WeightCsvFormat.fromRow(line, headers) }.getOrNull()
                if (e != null) { targets.weightRepository.save(e); imported++ }
            }
            BackupImporter.Result(weightImported = imported)
        }
        CsvType.DAILY_GOAL -> {
            val goal = runCatching { GoalCsvFormat.fromRow(lines[1], headers) }.getOrNull()
            if (goal != null) { targets.goalRepository.save(goal); BackupImporter.Result(goalRestored = true) }
            else BackupImporter.Result()
        }
        CsvType.CUSTOM_FOODS -> {
            var imported = 0
            lines.drop(1).forEach { line ->
                val f = runCatching { CustomFoodCsvFormat.fromRow(line, headers) }.getOrNull()
                if (f != null) { targets.customFoodRepository.save(f); imported++ }
            }
            BackupImporter.Result(customFoodsImported = imported)
        }
        CsvType.EXERCISES -> {
            val exercises = WorkoutBackup.parseExercises(lines)
            if (exercises.isNotEmpty()) targets.exerciseCatalogRepository.upsertAll(exercises)
            BackupImporter.Result(exercisesImported = exercises.size)
        }
        // Recognised but not standalone-importable — these reassemble only as a full backup ZIP.
        CsvType.WORKOUT_TEMPLATES, CsvType.TEMPLATE_EXERCISES,
        CsvType.WORKOUT_SESSIONS, CsvType.SESSION_EXERCISES, CsvType.SET_ENTRIES,
        CsvType.UNKNOWN,
        -> BackupImporter.Result()
    }
}
