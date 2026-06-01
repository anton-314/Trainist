package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Food

object CustomFoodCsvFormat {

    private const val NAME = "name"
    private const val BRAND = "brand"
    private const val KCAL = "kcal_per_100g"
    private const val PROTEIN = "protein_per_100g"
    private const val CARBS = "carbs_per_100g"
    private const val FAT = "fat_per_100g"
    private const val SUGAR = "sugar_per_100g"
    private const val FIBER = "fiber_per_100g"
    private const val SALT = "salt_per_100g"

    val HEADER: String = listOf(NAME, BRAND, KCAL, PROTEIN, CARBS, FAT, SUGAR, FIBER, SALT).joinToString(",")

    fun toRow(food: Food): String = listOf(
        food.name.escapeCsv(),
        food.brand?.escapeCsv() ?: "",
        food.kcalPer100g,
        food.proteinPer100g,
        food.carbsPer100g,
        food.fatPer100g,
        food.sugarPer100g,
        food.fiberPer100g,
        food.saltPer100g,
    ).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): Food? {
        val cols = CsvFormat.parseLine(row)
        val name = cols.str(headers, NAME)?.takeIf { it.isNotBlank() } ?: return null
        return Food(
            id = "",
            name = name,
            brand = cols.str(headers, BRAND)?.takeIf { it.isNotBlank() },
            kcalPer100g = cols.dbl(headers, KCAL) ?: 0.0,
            proteinPer100g = cols.dbl(headers, PROTEIN) ?: 0.0,
            carbsPer100g = cols.dbl(headers, CARBS) ?: 0.0,
            fatPer100g = cols.dbl(headers, FAT) ?: 0.0,
            sugarPer100g = cols.dbl(headers, SUGAR) ?: 0.0,
            fiberPer100g = cols.dbl(headers, FIBER) ?: 0.0,
            saltPer100g = cols.dbl(headers, SALT) ?: 0.0,
        )
    }

    fun parseHeaders(headerLine: String): Map<String, Int> =
        headerLine.split(",").mapIndexed { i, h -> h.trim() to i }.toMap()

    private fun String.escapeCsv(): String =
        if (any { it == ',' || it == '"' || it == '\n' })
            "\"${replace("\"", "\"\"")}\""
        else this

    private fun List<String>.str(headers: Map<String, Int>, col: String): String? =
        headers[col]?.let { getOrNull(it)?.trim() }

    private fun List<String>.dbl(headers: Map<String, Int>, col: String): Double? =
        str(headers, col)?.toDoubleOrNull()
}
