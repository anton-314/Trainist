package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem

/** ZIP entry names for the saved-meal sections, shared by the exporter and importer. */
object SavedMealBackupEntries {
    const val SAVED_MEALS = "saved_meals.csv"
    const val SAVED_MEAL_ITEMS = "saved_meal_items.csv"
}

/**
 * The [SavedMeal] header row (its ingredients live in [SavedMealItemCsvFormat], linked back by
 * [SavedMeal.stableId]). The row id is never exported.
 */
object SavedMealCsvFormat {

    const val STABLE_ID = "meal_stable_id"
    const val NAME = "meal_name"

    /** Optional; empty or missing (every backup from before it existed) = never logged. */
    const val LAST_USED_MS = "last_used_ms"

    val HEADER: String = listOf(STABLE_ID, NAME, LAST_USED_MS).joinToString(",")

    fun toRow(meal: SavedMeal): String = listOf(
        meal.stableId.escapeCsv(),
        meal.name.escapeCsv(),
        meal.lastUsedMs?.toString().orEmpty(),
    ).joinToString(",")

    /** Parses a header row into a meal without ingredients (attached on assembly). */
    fun fromRow(row: String, headers: Map<String, Int>): SavedMeal? {
        val cols = CsvFormat.parseLine(row)
        val stableId = cols.csvStr(headers, STABLE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val name = cols.csvStr(headers, NAME)?.takeIf { it.isNotBlank() } ?: return null
        return SavedMeal(
            stableId = stableId,
            name = name,
            items = emptyList(),
            lastUsedMs = cols.csvLong(headers, LAST_USED_MS),
        )
    }
}

/**
 * One ingredient row: the owning meal's stable key, its position and amount, followed by the food
 * snapshot in exactly the [CustomFoodCsvFormat] columns — an ingredient *is* a per-100 g food, so it
 * reuses that format (and its defaults for missing columns) instead of a second copy of it.
 */
object SavedMealItemCsvFormat {

    const val MEAL_STABLE_ID = SavedMealCsvFormat.STABLE_ID
    const val POSITION = "position"
    const val AMOUNT_GRAMS = "amount_grams"

    val HEADER: String = listOf(MEAL_STABLE_ID, POSITION, AMOUNT_GRAMS, CustomFoodCsvFormat.HEADER).joinToString(",")

    fun toRow(mealStableId: String, position: Int, item: SavedMealItem): String = listOf(
        mealStableId.escapeCsv(),
        position,
        item.amountGrams,
        CustomFoodCsvFormat.toRow(item.food),
    ).joinToString(",")

    /** An ingredient without a positive amount or a food name is malformed and dropped. */
    fun fromRow(row: String, headers: Map<String, Int>): Row? {
        val cols = CsvFormat.parseLine(row)
        val mealStableId = cols.csvStr(headers, MEAL_STABLE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val amount = cols.csvDbl(headers, AMOUNT_GRAMS)?.takeIf { it > 0 } ?: return null
        val food = CustomFoodCsvFormat.fromRow(row, headers) ?: return null
        return Row(mealStableId, cols.csvInt(headers, POSITION) ?: 0, SavedMealItem(food, amount))
    }

    data class Row(val mealStableId: String, val position: Int, val item: SavedMealItem)
}

/**
 * Pure reassembly of saved meals from their two flat sections, joined by the meal's stable key.
 * Absent sections (every backup written before saved meals existed) yield an empty list, no error.
 */
object SavedMealBackup {

    /** Meals whose ingredients all failed to parse are dropped — an empty meal logs nothing. */
    fun assemble(mealLines: List<String>?, itemLines: List<String>?): List<SavedMeal> {
        val meals = parseCsvRows(mealLines) { row, h -> SavedMealCsvFormat.fromRow(row, h) }
        if (meals.isEmpty()) return emptyList()

        val itemsByMeal = parseCsvRows(itemLines) { row, h -> SavedMealItemCsvFormat.fromRow(row, h) }
            .groupBy { it.mealStableId }

        return meals.mapNotNull { meal ->
            val items = itemsByMeal[meal.stableId].orEmpty()
                .sortedBy { it.position }
                .map { it.item }
            if (items.isEmpty()) null else meal.copy(items = items)
        }
    }
}
