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
        val cols = row.split(",")
        val date = headers[DATE]?.let { cols.getOrNull(it)?.trim() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val weightKg = headers[WEIGHT_KG]?.let { cols.getOrNull(it)?.trim()?.toDoubleOrNull() }
            ?: return null
        val timestampMs = headers[TIMESTAMP_MS]?.let { cols.getOrNull(it)?.trim()?.toLongOrNull() }
            ?: System.currentTimeMillis()
        return WeightEntry(weightKg = weightKg, date = date, timestampMs = timestampMs)
    }

    fun parseHeaders(headerLine: String): Map<String, Int> =
        headerLine.split(",").mapIndexed { i, h -> h.trim() to i }.toMap()
}
