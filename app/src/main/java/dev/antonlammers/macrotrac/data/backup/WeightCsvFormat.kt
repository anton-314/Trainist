package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import java.time.LocalDate

object WeightCsvFormat {
    private const val DATE = "date"
    private const val WEIGHT_KG = "weight_kg"
    private const val TIMESTAMP_MS = "timestamp_ms"

    val HEADER: String = listOf(DATE, WEIGHT_KG, TIMESTAMP_MS).joinToString(",")

    fun toRow(entry: WeightEntry): String =
        listOf(entry.date, entry.weightKg, entry.timestampMs).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): WeightEntry? {
        val cols = CsvFormat.parseLine(row)
        val date = cols.csvStr(headers, DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val weightKg = cols.csvDbl(headers, WEIGHT_KG) ?: return null
        val timestampMs = cols.csvDbl(headers, TIMESTAMP_MS)?.toLong() ?: System.currentTimeMillis()
        return WeightEntry(weightKg = weightKg, date = date, timestampMs = timestampMs)
    }
}
