package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMealItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FoodPortionsTest {

    private val date = LocalDate.parse("2026-10-28")

    @Test
    fun `entry scales every per-100g value by the amount`() {
        val entry = FoodPortions.entry(oats(), 60.0, MealCategory.BREAKFAST, FoodTag.HEALTHY, date, 1_000L)

        assertEquals(222.0, entry.kcal, 0.001)
        assertEquals(8.1, entry.proteinG, 0.001)
        assertEquals(35.4, entry.carbsG, 0.001)
        assertEquals(4.2, entry.fatG, 0.001)
        assertEquals(0.6, entry.sugarG, 0.001)
        assertEquals(6.0, entry.fiberG, 0.001)
        assertEquals(0.012, entry.saltG, 0.0001)
        assertEquals(60.0, entry.amountGrams, 0.0)
        assertEquals(MealCategory.BREAKFAST, entry.mealCategory)
        assertEquals(FoodTag.HEALTHY, entry.tag)
        assertEquals(date, entry.date)
        assertEquals(1_000L, entry.timestampMs)
        assertEquals("Haferflocken", entry.foodName)
        assertEquals("Kölln", entry.brand)
    }

    @Test
    fun `per100g inverts entry`() {
        val entry = FoodPortions.entry(oats(), 60.0, MealCategory.BREAKFAST, FoodTag.HEALTHY, date, 1_000L)

        val food = FoodPortions.per100g(entry)

        assertEquals(370.0, food.kcalPer100g, 0.001)
        assertEquals(13.5, food.proteinPer100g, 0.001)
        assertEquals(0.02, food.saltPer100g, 0.0001)
        assertEquals(FoodTag.HEALTHY, food.tag)
        assertEquals("Haferflocken", food.name)
    }

    @Test
    fun `per100g takes the totals as-is for an entry without a positive amount`() {
        val entry = FoodEntry(
            foodName = "Kaffee", brand = null, amountGrams = 0.0, kcal = 5.0,
            proteinG = 0.3, carbsG = 0.0, fatG = 0.0, date = date, timestampMs = 0L,
        )

        assertEquals(5.0, FoodPortions.per100g(entry).kcalPer100g, 0.0)
    }

    @Test
    fun `mealEntries creates one entry per ingredient with its own amount and tag`() {
        val items = listOf(SavedMealItem(oats(), 60.0), SavedMealItem(quark(), 150.0))

        val entries = FoodPortions.mealEntries(items, listOf(80.0, 150.0), MealCategory.SNACK, date, nowMs = 10_000L)

        assertEquals(listOf("Haferflocken", "Magerquark"), entries.map { it.foodName })
        assertEquals(listOf(80.0, 150.0), entries.map { it.amountGrams })
        assertEquals(296.0, entries[0].kcal, 0.001)
        assertEquals(listOf(FoodTag.HEALTHY, FoodTag.NEUTRAL), entries.map { it.tag })
        assertEquals(setOf(MealCategory.SNACK), entries.map { it.mealCategory }.toSet())
    }

    @Test
    fun `mealEntries keeps the ingredient order under the newest-first day view`() {
        val items = listOf(SavedMealItem(oats(), 60.0), SavedMealItem(quark(), 150.0), SavedMealItem(oats(), 10.0))

        val entries = FoodPortions.mealEntries(items, listOf(1.0, 1.0, 1.0), MealCategory.BREAKFAST, date, nowMs = 10_000L)

        // Sorted the way the Overview sorts (timestamp descending) the first ingredient stays first,
        // and no two entries share a timestamp.
        assertEquals(entries, entries.sortedByDescending { it.timestampMs })
        assertEquals(3, entries.map { it.timestampMs }.toSet().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mealEntries rejects a mismatched amount list`() {
        FoodPortions.mealEntries(listOf(SavedMealItem(oats(), 60.0)), emptyList(), MealCategory.SNACK, date, 0L)
    }

    @Test
    fun `totals sums kcal and macros over the ingredients at their amounts`() {
        val totals = FoodPortions.totals(listOf(SavedMealItem(oats(), 60.0), SavedMealItem(quark(), 150.0)))

        assertEquals(222.0 + 102.0, totals.kcal, 0.001)
        assertEquals(8.1 + 18.0, totals.proteinG, 0.001)
        assertEquals(35.4 + 6.0, totals.carbsG, 0.001)
        assertEquals(4.2 + 0.3, totals.fatG, 0.001)
    }

    @Test
    fun `totals of no ingredients is zero`() {
        assertEquals(NutritionTotals(), FoodPortions.totals(emptyList()))
    }

    private fun oats() = Food(
        id = "", name = "Haferflocken", brand = "Kölln",
        kcalPer100g = 370.0, proteinPer100g = 13.5, carbsPer100g = 59.0, fatPer100g = 7.0,
        sugarPer100g = 1.0, fiberPer100g = 10.0, saltPer100g = 0.02, tag = FoodTag.HEALTHY,
    )

    private fun quark() = Food(
        id = "", name = "Magerquark", brand = null,
        kcalPer100g = 68.0, proteinPer100g = 12.0, carbsPer100g = 4.0, fatPer100g = 0.2,
        tag = FoodTag.NEUTRAL,
    )
}
