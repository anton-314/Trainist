package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomFoodCsvFormatTest {

    private val headers = CsvFormat.parseHeaders(CustomFoodCsvFormat.HEADER)

    @Test
    fun `toRow and fromRow round-trip preserves all fields`() {
        val food = buildFood(name = "Haferflocken", brand = "Kölln", saltPer100g = 0.02)
        val row = CustomFoodCsvFormat.toRow(food)
        val parsed = CustomFoodCsvFormat.fromRow(row, headers)!!

        assertEquals(food.name, parsed.name)
        assertEquals(food.brand, parsed.brand)
        assertEquals(food.kcalPer100g, parsed.kcalPer100g, 0.001)
        assertEquals(food.proteinPer100g, parsed.proteinPer100g, 0.001)
        assertEquals(food.carbsPer100g, parsed.carbsPer100g, 0.001)
        assertEquals(food.fatPer100g, parsed.fatPer100g, 0.001)
        assertEquals(food.sugarPer100g, parsed.sugarPer100g, 0.001)
        assertEquals(food.fiberPer100g, parsed.fiberPer100g, 0.001)
        assertEquals(food.saltPer100g, parsed.saltPer100g, 0.001)
    }

    @Test
    fun `fromRow returns null when name is blank`() {
        val row = CustomFoodCsvFormat.toRow(buildFood(name = "x")).replace("x", "")
        assertNull(CustomFoodCsvFormat.fromRow(row, headers))
    }

    @Test
    fun `fromRow uses defaults for missing optional columns`() {
        val sparseHeaders = CsvFormat.parseHeaders("name")
        val food = CustomFoodCsvFormat.fromRow("Apfel", sparseHeaders)!!
        assertEquals("Apfel", food.name)
        assertEquals(0.0, food.kcalPer100g, 0.001)
        assertEquals(0.0, food.saltPer100g, 0.001)
    }

    @Test
    fun `fromRow uses 0 as default for salt when column absent (backward compat)`() {
        val oldHeader = "name,brand,kcal_per_100g,protein_per_100g,carbs_per_100g,fat_per_100g,sugar_per_100g,fiber_per_100g"
        val oldHeaders = CsvFormat.parseHeaders(oldHeader)
        val row = "Haferflocken,Kölln,370.0,13.0,59.0,7.0,1.0,8.0"
        val food = CustomFoodCsvFormat.fromRow(row, oldHeaders)!!
        assertEquals(0.0, food.saltPer100g, 0.001)
        assertEquals(1.0, food.sugarPer100g, 0.001)
    }

    @Test
    fun `toRow escapes commas in name`() {
        val food = buildFood(name = "Salz, Pfeffer Mix")
        val row = CustomFoodCsvFormat.toRow(food)
        assert(row.contains("\"Salz, Pfeffer Mix\"")) { "Expected quoted name in: $row" }
    }

    @Test
    fun `fromRow handles null brand (empty string)`() {
        val food = buildFood(brand = null)
        val row = CustomFoodCsvFormat.toRow(food)
        val parsed = CustomFoodCsvFormat.fromRow(row, headers)!!
        assertNull(parsed.brand)
    }

    @Test
    fun `id is always empty string on import`() {
        val food = buildFood()
        val parsed = CustomFoodCsvFormat.fromRow(CustomFoodCsvFormat.toRow(food), headers)!!
        assertEquals("", parsed.id)
    }

    private fun buildFood(
        name: String = "Test",
        brand: String? = "TestBrand",
        saltPer100g: Double = 0.0,
    ) = Food(
        id = "42",
        name = name,
        brand = brand,
        kcalPer100g = 370.0,
        proteinPer100g = 13.0,
        carbsPer100g = 59.0,
        fatPer100g = 7.0,
        sugarPer100g = 1.0,
        fiberPer100g = 8.0,
        saltPer100g = saltPer100g,
    )
}
