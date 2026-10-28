package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMealBackupTest {

    @Test
    fun `meals and their ingredients round-trip through the two sections`() {
        val meals = listOf(muesli(), meal("b", "Abendbrot, schnell", listOf(item("Brot \"Vollkorn\"", 80.0))))

        val assembled = SavedMealBackup.assemble(mealLines(meals), itemLines(meals))

        assertEquals(meals, assembled)
    }

    @Test
    fun `ingredients are re-ordered by position, not by row order`() {
        val meal = muesli()
        val rows = itemLines(listOf(meal))
        val shuffled = listOf(rows.first()) + rows.drop(1).reversed()

        val assembled = SavedMealBackup.assemble(mealLines(listOf(meal)), shuffled)

        assertEquals(meal.items, assembled.single().items)
    }

    @Test
    fun `absent sections - every backup from before saved meals - yield nothing`() {
        assertTrue(SavedMealBackup.assemble(null, null).isEmpty())
        assertTrue(SavedMealBackup.assemble(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `a meal without any parseable ingredient is dropped`() {
        val meals = listOf(muesli(), meal("empty", "Leer", emptyList()))

        val assembled = SavedMealBackup.assemble(mealLines(meals), itemLines(meals))

        assertEquals(listOf("Müsli"), assembled.map { it.name })
    }

    @Test
    fun `an ingredient without a positive amount is malformed`() {
        val headers = CsvFormat.parseHeaders(SavedMealItemCsvFormat.HEADER)
        val row = SavedMealItemCsvFormat.toRow("a", 0, item("Milch", 200.0)).replace(",200.0,", ",0,")

        assertNull(SavedMealItemCsvFormat.fromRow(row, headers))
    }

    @Test
    fun `ingredient columns missing from an older format fall back to custom-food defaults`() {
        val headers = CsvFormat.parseHeaders("meal_stable_id,amount_grams,name,kcal_per_100g")

        val row = SavedMealItemCsvFormat.fromRow("a,150,Quark,68", headers)!!

        assertEquals(0, row.position)
        assertEquals(FoodTag.NONE, row.item.food.tag)
        assertEquals(0.0, row.item.food.proteinPer100g, 0.0)
        assertEquals(68.0, row.item.food.kcalPer100g, 0.0)
    }

    @Test
    fun `both sections are detected by their own column, ahead of the custom-food rule`() {
        val mealHeaders = CsvFormat.parseHeaders(SavedMealCsvFormat.HEADER)
        val itemHeaders = CsvFormat.parseHeaders(SavedMealItemCsvFormat.HEADER)

        assertEquals(CsvType.SAVED_MEALS, detectCsvType(mealHeaders))
        // The ingredient section carries every custom-food column, kcal_per_100g included.
        assertTrue("kcal_per_100g" in itemHeaders)
        assertEquals(CsvType.SAVED_MEAL_ITEMS, detectCsvType(itemHeaders))
        assertEquals(CsvType.CUSTOM_FOODS, detectCsvType(CsvFormat.parseHeaders(CustomFoodCsvFormat.HEADER)))
    }

    private fun mealLines(meals: List<SavedMeal>) =
        listOf(SavedMealCsvFormat.HEADER) + meals.map { SavedMealCsvFormat.toRow(it) }

    private fun itemLines(meals: List<SavedMeal>) =
        listOf(SavedMealItemCsvFormat.HEADER) + meals.flatMap { meal ->
            meal.items.mapIndexed { index, item -> SavedMealItemCsvFormat.toRow(meal.stableId, index, item) }
        }

    private fun muesli() = meal(
        "a", "Müsli",
        listOf(item("Haferflocken", 60.0, FoodTag.HEALTHY), item("Magerquark", 150.0), item("Milch, 1,5 %", 200.0, FoodTag.NEUTRAL)),
    )

    private fun meal(stableId: String, name: String, items: List<SavedMealItem>) =
        SavedMeal(stableId = stableId, name = name, items = items)

    private fun item(name: String, amount: Double, tag: FoodTag = FoodTag.NONE) = SavedMealItem(
        food = Food(
            id = "", name = name, brand = null,
            kcalPer100g = 100.0, proteinPer100g = 10.0, carbsPer100g = 5.0, fatPer100g = 1.0,
            sugarPer100g = 2.0, fiberPer100g = 3.0, saltPer100g = 0.1, tag = tag,
        ),
        amountGrams = amount,
    )
}
