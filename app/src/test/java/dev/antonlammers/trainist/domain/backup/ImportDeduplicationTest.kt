package dev.antonlammers.trainist.domain.backup

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportDeduplicationTest {

    private val date = LocalDate.parse("2026-06-01")

    private fun entry(name: String = "Haferflocken", amount: Double = 80.0, timestamp: Long = 1_000L, id: Long = 0) =
        FoodEntry(
            id = id, foodName = name, brand = "EDEKA Bio", amountGrams = amount, kcal = amount * 3.7,
            proteinG = 10.8, carbsG = 47.0, fatG = 5.6, mealCategory = MealCategory.BREAKFAST,
            date = date, timestampMs = timestamp,
        )

    private fun food(name: String = "Magerquark", kcal: Double = 67.0, id: String = "") =
        Food(id = id, name = name, brand = null, kcalPer100g = kcal, proteinPer100g = 12.0, carbsPer100g = 4.0, fatPer100g = 0.3)

    @Test
    fun `an entry identical to a stored one is dropped although the row ids differ`() {
        val fresh = ImportDeduplication.newFoodEntries(existing = listOf(entry(id = 17)), incoming = listOf(entry()))

        assertTrue(fresh.isEmpty())
    }

    @Test
    fun `copies within the import itself collapse to the first one`() {
        val incoming = List(8) { entry() } + List(8) { entry(name = "Apfel", timestamp = 2_000L) }

        val fresh = ImportDeduplication.newFoodEntries(existing = emptyList(), incoming = incoming)

        assertEquals(listOf("Haferflocken", "Apfel"), fresh.map { it.foodName })
    }

    @Test
    fun `entries differing in any field are kept`() {
        val stored = entry()
        val incoming = listOf(
            entry(amount = 100.0),
            entry(timestamp = 1_001L),
            stored.copy(date = date.plusDays(1)),
            stored.copy(mealCategory = MealCategory.SNACK),
            stored.copy(tag = FoodTag.HEALTHY),
            stored.copy(brand = null),
        )

        val fresh = ImportDeduplication.newFoodEntries(existing = listOf(stored.copy(id = 1)), incoming = incoming)

        assertEquals(incoming, fresh)
    }

    @Test
    fun `incoming order is preserved`() {
        val incoming = listOf(entry(name = "C", timestamp = 3), entry(name = "A", timestamp = 1), entry(name = "B", timestamp = 2))

        assertEquals(incoming, ImportDeduplication.newFoodEntries(emptyList(), incoming))
    }

    @Test
    fun `custom foods compare by every field but the id`() {
        val existing = listOf(food(id = "5"))
        val incoming = listOf(food(), food(), food(kcal = 70.0), food(name = "Skyr"))

        val fresh = ImportDeduplication.newCustomFoods(existing, incoming)

        assertEquals(listOf(food(kcal = 70.0), food(name = "Skyr")), fresh)
    }

    @Test
    fun `nothing stored and nothing incoming yields nothing`() {
        assertTrue(ImportDeduplication.newFoodEntries(emptyList(), emptyList()).isEmpty())
        assertTrue(ImportDeduplication.newCustomFoods(listOf(food(id = "1")), emptyList()).isEmpty())
    }
}
