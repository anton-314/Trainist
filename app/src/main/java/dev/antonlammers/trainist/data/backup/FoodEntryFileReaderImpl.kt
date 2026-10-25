package dev.antonlammers.trainist.data.backup

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.trainist.domain.backup.FoodEntryFile
import dev.antonlammers.trainist.domain.backup.FoodEntryFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodEntryFileReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FoodEntryFileReader {

    override suspend fun read(uri: String): FoodEntryFile = withContext(Dispatchers.IO) {
        // A revoked grant, a vanished download or a provider that refuses the stream all surface as
        // exceptions here — none of them is the user's file being "wrong", so they share one outcome.
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext FoodEntryFile.Unreadable
        parseFoodEntryFile(bytes)
    }
}

/**
 * Pure counterpart of [FoodEntryFileReaderImpl.read]: decides what [bytes] contain and parses the
 * food entries out of them. Only a food-entries CSV is accepted — a ZIP or any other CSV section is
 * [FoodEntryFile.NotFoodEntries], never imported partially.
 */
internal fun parseFoodEntryFile(bytes: ByteArray): FoodEntryFile {
    if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
        return FoodEntryFile.NotFoodEntries
    }
    val lines = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return FoodEntryFile.NotFoodEntries

    val headers = CsvFormat.parseHeaders(lines.first())
    if (detectCsvType(headers) != CsvType.FOOD_ENTRIES) return FoodEntryFile.NotFoodEntries

    val rows = lines.drop(1)
    val entries = rows.mapNotNull { runCatching { CsvFormat.fromRow(it, headers) }.getOrNull() }
    return if (entries.isEmpty()) {
        FoodEntryFile.NoValidRows
    } else {
        FoodEntryFile.Entries(entries, skipped = rows.size - entries.size)
    }
}
