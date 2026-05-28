package dev.antonlammers.macrotrac.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FoodEntryRepository,
) {
    data class Result(val imported: Int, val skipped: Int)

    suspend fun import(uri: Uri): Result {
        val lines = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.readLines()
            ?.filter { it.isNotBlank() }
            ?: return Result(0, 0)

        if (lines.isEmpty()) return Result(0, 0)

        val headers = CsvFormat.parseHeaders(lines.first())
        var imported = 0
        var skipped = 0

        lines.drop(1).forEach { line ->
            val entry = runCatching { CsvFormat.fromRow(line, headers) }.getOrNull()
            if (entry != null) {
                repository.add(entry)
                imported++
            } else {
                skipped++
            }
        }

        return Result(imported, skipped)
    }
}
