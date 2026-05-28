package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.FoodEntry
import java.time.LocalDate

/**
 * Pure Kotlin CSV serialisation/deserialisation — no Android dependencies, fully unit-testable.
 * Extensibility: add a column to CsvColumns.HEADER, write it in [toRow], read it in [fromRow].
 * Import ignores unknown columns and fills missing ones with defaults, so old exports stay valid.
 */
object CsvFormat {

    fun toRow(entry: FoodEntry): String = listOf(
        entry.date.toString(),
        entry.foodName.escapeCsv(),
        entry.brand?.escapeCsv() ?: "",
        entry.amountGrams,
        entry.kcal,
        entry.proteinG,
        entry.carbsG,
        entry.fatG,
        entry.timestampMs,
    ).joinToString(",")

    /**
     * Parses a single data row using a header→index map.
     * Returns null if the row is malformed or missing required fields.
     */
    fun fromRow(row: String, headers: Map<String, Int>): FoodEntry? {
        val cols = parseLine(row)
        val date = cols.str(headers, CsvColumns.DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return null
        val foodName = cols.str(headers, CsvColumns.FOOD_NAME)?.takeIf { it.isNotBlank() }
            ?: return null
        return FoodEntry(
            foodName = foodName,
            brand = cols.str(headers, CsvColumns.BRAND)?.takeIf { it.isNotBlank() },
            amountGrams = cols.dbl(headers, CsvColumns.AMOUNT_GRAMS) ?: 100.0,
            kcal = cols.dbl(headers, CsvColumns.KCAL) ?: 0.0,
            proteinG = cols.dbl(headers, CsvColumns.PROTEIN_G) ?: 0.0,
            carbsG = cols.dbl(headers, CsvColumns.CARBS_G) ?: 0.0,
            fatG = cols.dbl(headers, CsvColumns.FAT_G) ?: 0.0,
            date = date,
            timestampMs = cols.dbl(headers, CsvColumns.TIMESTAMP_MS)?.toLong()
                ?: System.currentTimeMillis(),
        )
    }

    fun parseHeaders(headerLine: String): Map<String, Int> =
        headerLine.split(",").mapIndexed { i, h -> h.trim() to i }.toMap()

    // RFC-4180 quoting: wrap in " if value contains comma, quote, or newline
    private fun String.escapeCsv(): String =
        if (any { it == ',' || it == '"' || it == '\n' })
            "\"${replace("\"", "\"\"")}\""
        else this

    // RFC-4180 parser: handles quoted fields with embedded commas and escaped quotes
    internal fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    buf.append('"'); i++
                }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> { result.add(buf.toString()); buf.clear() }
                else -> buf.append(c)
            }
            i++
        }
        result.add(buf.toString())
        return result
    }

    private fun List<String>.str(headers: Map<String, Int>, col: String): String? =
        headers[col]?.let { getOrNull(it)?.trim() }

    private fun List<String>.dbl(headers: Map<String, Int>, col: String): Double? =
        str(headers, col)?.toDoubleOrNull()
}
