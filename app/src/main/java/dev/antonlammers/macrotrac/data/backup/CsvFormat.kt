package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.FoodTag
import dev.antonlammers.macrotrac.domain.model.MealCategory
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
        entry.sugarG,
        entry.fiberG,
        entry.saltG,
        entry.mealCategory.name,
        entry.tag.name,
        entry.timestampMs,
    ).joinToString(",")

    /**
     * Parses a single data row using a header→index map.
     * Returns null if the row is malformed or missing required fields.
     */
    fun fromRow(row: String, headers: Map<String, Int>): FoodEntry? {
        val cols = parseLine(row)
        val date = cols.csvStr(headers, CsvColumns.DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return null
        val foodName = cols.csvStr(headers, CsvColumns.FOOD_NAME)?.takeIf { it.isNotBlank() }
            ?: return null
        return FoodEntry(
            foodName = foodName,
            brand = cols.csvStr(headers, CsvColumns.BRAND)?.takeIf { it.isNotBlank() },
            amountGrams = cols.csvDbl(headers, CsvColumns.AMOUNT_GRAMS) ?: 100.0,
            kcal = cols.csvDbl(headers, CsvColumns.KCAL) ?: 0.0,
            proteinG = cols.csvDbl(headers, CsvColumns.PROTEIN_G) ?: 0.0,
            carbsG = cols.csvDbl(headers, CsvColumns.CARBS_G) ?: 0.0,
            fatG = cols.csvDbl(headers, CsvColumns.FAT_G) ?: 0.0,
            sugarG = cols.csvDbl(headers, CsvColumns.SUGAR_G) ?: 0.0,
            fiberG = cols.csvDbl(headers, CsvColumns.FIBER_G) ?: 0.0,
            saltG = cols.csvDbl(headers, CsvColumns.SALT_G) ?: 0.0,
            mealCategory = cols.csvStr(headers, CsvColumns.MEAL_CATEGORY)
                ?.let { runCatching { MealCategory.valueOf(it) }.getOrNull() }
                ?: MealCategory.SNACK,
            tag = FoodTag.parse(cols.csvStr(headers, CsvColumns.TAG)),
            date = date,
            timestampMs = cols.csvDbl(headers, CsvColumns.TIMESTAMP_MS)?.toLong()
                ?: System.currentTimeMillis(),
        )
    }

    fun parseHeaders(headerLine: String): Map<String, Int> =
        headerLine.split(",").mapIndexed { i, h -> h.trim() to i }.toMap()

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

}

// Shared CSV helpers — used by CsvFormat and CustomFoodCsvFormat
internal fun String.escapeCsv(): String =
    if (any { it == ',' || it == '"' || it == '\n' })
        "\"${replace("\"", "\"\"")}\""
    else this

internal fun List<String>.csvStr(headers: Map<String, Int>, col: String): String? =
    headers[col]?.let { getOrNull(it)?.trim() }

internal fun List<String>.csvDbl(headers: Map<String, Int>, col: String): Double? =
    csvStr(headers, col)?.toDoubleOrNull()

internal fun List<String>.csvInt(headers: Map<String, Int>, col: String): Int? =
    csvStr(headers, col)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.toInt()

internal fun List<String>.csvLong(headers: Map<String, Int>, col: String): Long? =
    csvStr(headers, col)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.toLong()

internal fun List<String>.csvBool(headers: Map<String, Int>, col: String): Boolean? =
    csvStr(headers, col)?.takeIf { it.isNotBlank() }?.let { it.equals("true", ignoreCase = true) || it == "1" }
