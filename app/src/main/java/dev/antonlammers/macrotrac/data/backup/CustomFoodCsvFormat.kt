package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodTag

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
    private const val TAG = "tag"

    val HEADER: String = listOf(NAME, BRAND, KCAL, PROTEIN, CARBS, FAT, SUGAR, FIBER, SALT, TAG).joinToString(",")

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
        food.tag.name,
    ).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): Food? {
        val cols = CsvFormat.parseLine(row)
        val name = cols.csvStr(headers, NAME)?.takeIf { it.isNotBlank() } ?: return null
        return Food(
            id = "",
            name = name,
            brand = cols.csvStr(headers, BRAND)?.takeIf { it.isNotBlank() },
            kcalPer100g = cols.csvDbl(headers, KCAL) ?: 0.0,
            proteinPer100g = cols.csvDbl(headers, PROTEIN) ?: 0.0,
            carbsPer100g = cols.csvDbl(headers, CARBS) ?: 0.0,
            fatPer100g = cols.csvDbl(headers, FAT) ?: 0.0,
            sugarPer100g = cols.csvDbl(headers, SUGAR) ?: 0.0,
            fiberPer100g = cols.csvDbl(headers, FIBER) ?: 0.0,
            saltPer100g = cols.csvDbl(headers, SALT) ?: 0.0,
            tag = FoodTag.parse(cols.csvStr(headers, TAG)),
        )
    }
}
