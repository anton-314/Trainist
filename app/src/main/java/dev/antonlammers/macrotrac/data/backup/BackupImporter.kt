package dev.antonlammers.macrotrac.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import dev.antonlammers.macrotrac.domain.repository.WeightRepository
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodEntryRepository: FoodEntryRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
    private val customFoodRepository: CustomFoodRepository,
) {
    data class Result(
        val foodImported: Int = 0,
        val foodSkipped: Int = 0,
        val weightImported: Int = 0,
        val goalRestored: Boolean = false,
        val customFoodsImported: Int = 0,
    )

    suspend fun import(uri: Uri): Result =
        if (isZip(uri)) importZip(uri) else importLegacyCsv(uri)

    private fun isZip(uri: Uri): Boolean =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(2)
            stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } ?: false

    private suspend fun importZip(uri: Uri): Result {
        var foodImported = 0
        var foodSkipped = 0
        var weightImported = 0
        var goalRestored = false
        var customFoodsImported = 0

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    val lines = content.lines().filter { it.isNotBlank() }
                    when (entry.name) {
                        "food_entries.csv" -> if (lines.size > 1) {
                            val headers = CsvFormat.parseHeaders(lines.first())
                            lines.drop(1).forEach { line ->
                                val e = runCatching { CsvFormat.fromRow(line, headers) }.getOrNull()
                                if (e != null) { foodEntryRepository.add(e); foodImported++ }
                                else foodSkipped++
                            }
                        }
                        "weight_entries.csv" -> if (lines.size > 1) {
                            val headers = WeightCsvFormat.parseHeaders(lines.first())
                            lines.drop(1).forEach { line ->
                                val e = runCatching { WeightCsvFormat.fromRow(line, headers) }.getOrNull()
                                if (e != null) { weightRepository.save(e); weightImported++ }
                            }
                        }
                        "daily_goal.csv" -> if (lines.size > 1) {
                            val headers = GoalCsvFormat.parseHeaders(lines.first())
                            val goal = runCatching { GoalCsvFormat.fromRow(lines[1], headers) }.getOrNull()
                            if (goal != null) { goalRepository.save(goal); goalRestored = true }
                        }
                        "custom_foods.csv" -> if (lines.size > 1) {
                            val headers = CustomFoodCsvFormat.parseHeaders(lines.first())
                            lines.drop(1).forEach { line ->
                                val f = runCatching { CustomFoodCsvFormat.fromRow(line, headers) }.getOrNull()
                                if (f != null) { customFoodRepository.save(f); customFoodsImported++ }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return Result(foodImported, foodSkipped, weightImported, goalRestored, customFoodsImported)
    }

    private suspend fun importLegacyCsv(uri: Uri): Result {
        val lines = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.readLines()
            ?.filter { it.isNotBlank() }
            ?: return Result()

        if (lines.isEmpty()) return Result()

        val headers = CsvFormat.parseHeaders(lines.first())
        var imported = 0
        var skipped = 0

        lines.drop(1).forEach { line ->
            val e = runCatching { CsvFormat.fromRow(line, headers) }.getOrNull()
            if (e != null) { foodEntryRepository.add(e); imported++ }
            else skipped++
        }

        return Result(foodImported = imported, foodSkipped = skipped)
    }
}
