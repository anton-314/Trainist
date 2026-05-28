package dev.antonlammers.macromind.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.macromind.domain.repository.FoodEntryRepository
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FoodEntryRepository,
) {
    suspend fun export(): Uri {
        val entries = repository.allEntries()
            .sortedWith(compareBy({ it.date.toString() }, { it.timestampMs }))

        val csv = buildString {
            appendLine(CsvColumns.HEADER)
            entries.forEach { appendLine(CsvFormat.toRow(it)) }
        }

        val file = File(context.cacheDir, "macromind_${LocalDate.now()}.csv")
        file.writeText(csv, Charsets.UTF_8)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
