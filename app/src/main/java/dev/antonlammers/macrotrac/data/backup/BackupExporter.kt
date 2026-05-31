package dev.antonlammers.macrotrac.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
) {
    suspend fun export(): Uri {
        val foodEntries = foodEntryRepository.allEntries()
            .sortedWith(compareBy({ it.date.toString() }, { it.timestampMs }))
        val weightEntries = weightRepository.allEntries()
        val goal = goalRepository.goal().first()

        val file = File(context.cacheDir, "macrotrac_backup_${LocalDate.now()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putEntry("food_entries.csv", buildFoodCsv(foodEntries))
            zip.putEntry("weight_entries.csv", buildWeightCsv(weightEntries))
            zip.putEntry("daily_goal.csv", buildGoalCsv(goal))
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
}
