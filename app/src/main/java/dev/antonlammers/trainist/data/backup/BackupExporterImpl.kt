package dev.antonlammers.trainist.data.backup

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.trainist.domain.backup.BackupExporter
import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.domain.repository.BodyMeasurementRepository
import dev.antonlammers.trainist.domain.repository.CustomFoodRepository
import dev.antonlammers.trainist.domain.repository.ExerciseCatalogRepository
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.domain.repository.WorkoutSessionRepository
import dev.antonlammers.trainist.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupExporterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
    private val customFoodRepository: CustomFoodRepository,
    private val exerciseCatalogRepository: ExerciseCatalogRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
) : BackupExporter {
    override suspend fun export(): String {
        val foodEntries = foodEntryRepository.allEntries()
            .sortedWith(compareBy({ it.date.toString() }, { it.timestampMs }))
        val weightEntries = weightRepository.allEntries()
        val goal = goalRepository.goal().first()
        val customFoods = customFoodRepository.allFoods().first()
        val measurements = bodyMeasurementRepository.allEntries()

        val templates = workoutTemplateRepository.templates().first()
        val sessions = workoutSessionRepository.sessions().first()
        // Only custom exercises need to travel — catalog entries are reproduced by seeding on every
        // install and would bloat every backup. Any referenced catalog exercise resolves against the
        // target's seeded catalog by its stable id.
        val exercises = exerciseCatalogRepository.exercises().first().filter { it.isCustom }

        val file = File(context.cacheDir, "trainist_backup_${LocalDate.now()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putEntry("food_entries.csv", buildFoodCsv(foodEntries))
            zip.putEntry("weight_entries.csv", buildWeightCsv(weightEntries))
            zip.putEntry("daily_goal.csv", buildGoalCsv(goal))
            zip.putEntry("custom_foods.csv", buildCustomFoodCsv(customFoods))
            zip.putEntry("body_measurements.csv", buildMeasurementsCsv(measurements))
            zip.putEntry(WorkoutBackupEntries.EXERCISES, buildExercisesCsv(exercises))
            zip.putEntry(WorkoutBackupEntries.WORKOUT_TEMPLATES, buildTemplatesCsv(templates))
            zip.putEntry(WorkoutBackupEntries.TEMPLATE_EXERCISES, buildTemplateExercisesCsv(templates))
            zip.putEntry(WorkoutBackupEntries.WORKOUT_SESSIONS, buildSessionsCsv(sessions))
            zip.putEntry(WorkoutBackupEntries.SESSION_EXERCISES, buildSessionExercisesCsv(sessions))
            zip.putEntry(WorkoutBackupEntries.SET_ENTRIES, buildSetEntriesCsv(sessions))
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildFoodCsv(entries: List<FoodEntry>) = buildString {
        appendLine(CsvColumns.HEADER)
        entries.forEach { appendLine(CsvFormat.toRow(it)) }
    }

    private fun buildWeightCsv(entries: List<WeightEntry>) = buildString {
        appendLine(WeightCsvFormat.HEADER)
        entries.forEach { appendLine(WeightCsvFormat.toRow(it)) }
    }

    private fun buildGoalCsv(goal: DailyGoal) = buildString {
        appendLine(GoalCsvFormat.HEADER)
        appendLine(GoalCsvFormat.toRow(goal))
    }

    private fun buildCustomFoodCsv(foods: List<Food>) = buildString {
        appendLine(CustomFoodCsvFormat.HEADER)
        foods.forEach { appendLine(CustomFoodCsvFormat.toRow(it)) }
    }

    private fun buildMeasurementsCsv(entries: List<BodyMeasurementEntry>) = buildString {
        appendLine(BodyMeasurementCsvFormat.HEADER)
        entries.forEach { appendLine(BodyMeasurementCsvFormat.toRow(it)) }
    }

    private fun buildExercisesCsv(exercises: List<Exercise>) = buildString {
        appendLine(ExerciseCsvFormat.HEADER)
        exercises.forEach { appendLine(ExerciseCsvFormat.toRow(it)) }
    }

    private fun buildTemplatesCsv(templates: List<WorkoutTemplate>) = buildString {
        appendLine(WorkoutTemplateCsvFormat.HEADER)
        templates.forEach { appendLine(WorkoutTemplateCsvFormat.toRow(it)) }
    }

    private fun buildTemplateExercisesCsv(templates: List<WorkoutTemplate>) = buildString {
        appendLine(TemplateExerciseCsvFormat.HEADER)
        templates.forEach { template ->
            template.exercises.forEach { appendLine(TemplateExerciseCsvFormat.toRow(template.stableId, it)) }
        }
    }

    private fun buildSessionsCsv(sessions: List<WorkoutSession>) = buildString {
        appendLine(WorkoutSessionCsvFormat.HEADER)
        sessions.forEach { appendLine(WorkoutSessionCsvFormat.toRow(it)) }
    }

    private fun buildSessionExercisesCsv(sessions: List<WorkoutSession>) = buildString {
        appendLine(SessionExerciseCsvFormat.HEADER)
        sessions.forEach { session ->
            session.exercises.forEach { appendLine(SessionExerciseCsvFormat.toRow(session.stableId, it)) }
        }
    }

    private fun buildSetEntriesCsv(sessions: List<WorkoutSession>) = buildString {
        appendLine(SetEntryCsvFormat.HEADER)
        sessions.forEach { session ->
            session.exercises.forEach { exercise ->
                exercise.sets.forEach { appendLine(SetEntryCsvFormat.toRow(session.stableId, exercise.position, it)) }
            }
        }
    }
}
