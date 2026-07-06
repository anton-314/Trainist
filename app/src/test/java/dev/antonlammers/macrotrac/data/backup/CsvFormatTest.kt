package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.FoodTag
import dev.antonlammers.macrotrac.domain.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvFormatTest {

    private val headers = CsvFormat.parseHeaders(CsvColumns.HEADER)

    // ── parseLine ────────────────────────────────────────────────────────────

    @Test
    fun `parseLine splits simple row`() {
        val cols = CsvFormat.parseLine("2026-05-28,Apfel,,150.0,78.0,0.5,21.0,0.3,2.0,1.5,SNACK,1748000000000")
        assertEquals("2026-05-28", cols[0])
        assertEquals("Apfel", cols[1])
        assertEquals("", cols[2])
        assertEquals("150.0", cols[3])
    }

    @Test
    fun `parseLine handles quoted field with comma`() {
        val cols = CsvFormat.parseLine("2026-05-28,\"Hähnchen, gegrillt\",,200.0,240.0,48.0,0.0,5.0,0.0,0.0,LUNCH,1748000000000")
        assertEquals("Hähnchen, gegrillt", cols[1])
    }

    @Test
    fun `parseLine handles escaped quote inside quoted field`() {
        val cols = CsvFormat.parseLine("2026-05-28,\"Bio\"\"Joghurt\",,150.0,90.0,6.0,9.0,3.0,0.0,0.0,BREAKFAST,1748000000000")
        assertEquals("Bio\"Joghurt", cols[1])
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    fun `toRow and fromRow round-trip preserves all fields`() {
        val entry = buildEntry(foodName = "Vollkornbrot", brand = "Mestemacher", amountGrams = 60.0)
        val row = CsvFormat.toRow(entry)
        val parsed = CsvFormat.fromRow(row, headers)!!

        assertEquals(entry.foodName, parsed.foodName)
        assertEquals(entry.brand, parsed.brand)
        assertEquals(entry.amountGrams, parsed.amountGrams, 0.001)
        assertEquals(entry.kcal, parsed.kcal, 0.001)
        assertEquals(entry.date, parsed.date)
    }

    @Test
    fun `toRow escapes commas in food name`() {
        val entry = buildEntry(foodName = "Salz, Pfeffer")
        val row = CsvFormat.toRow(entry)
        assert(row.contains("\"Salz, Pfeffer\"")) { "Expected quoted field in: $row" }
    }

    @Test
    fun `fromRow returns null when date is missing`() {
        val badHeaders = mapOf("food_name" to 0, "kcal" to 1)
        val result = CsvFormat.fromRow("Apfel,52.0", badHeaders)
        assertNull(result)
    }

    @Test
    fun `fromRow returns null when food_name is blank`() {
        val row = CsvFormat.toRow(buildEntry(foodName = "x")).replace("x", "")
        assertNull(CsvFormat.fromRow(row, headers))
    }

    @Test
    fun `fromRow uses defaults for missing optional columns`() {
        val sparseHeaders = mapOf(CsvColumns.DATE to 0, CsvColumns.FOOD_NAME to 1)
        val entry = CsvFormat.fromRow("2026-05-28,Ei", sparseHeaders)!!
        assertEquals(100.0, entry.amountGrams, 0.001)
        assertEquals(0.0, entry.kcal, 0.001)
        assertEquals(LocalDate.parse("2026-05-28"), entry.date)
        assertEquals(0.0, entry.sugarG, 0.001)
        assertEquals(0.0, entry.fiberG, 0.001)
        assertEquals(0.0, entry.saltG, 0.001)
    }

    @Test
    fun `fromRow uses 0 as default for salt_g when column is absent (backward compat)`() {
        // Simulate an old export that has no salt_g column
        val oldHeader = listOf(
            CsvColumns.DATE, CsvColumns.FOOD_NAME, CsvColumns.BRAND, CsvColumns.AMOUNT_GRAMS,
            CsvColumns.KCAL, CsvColumns.PROTEIN_G, CsvColumns.CARBS_G, CsvColumns.FAT_G,
            CsvColumns.SUGAR_G, CsvColumns.FIBER_G, CsvColumns.MEAL_CATEGORY, CsvColumns.TIMESTAMP_MS,
        ).joinToString(",")
        val oldHeaders = CsvFormat.parseHeaders(oldHeader)
        val row = "2026-05-28,Apfel,,150.0,78.0,0.5,21.0,0.3,2.0,1.5,SNACK,1748000000000"
        val entry = CsvFormat.fromRow(row, oldHeaders)!!
        assertEquals(0.0, entry.saltG, 0.001)
        assertEquals(2.0, entry.sugarG, 0.001)
    }

    @Test
    fun `fromRow with extra columns parses known fields correctly`() {
        val extendedHeader = "${CsvColumns.HEADER},unknown_col"
        val extendedHeaders = CsvFormat.parseHeaders(extendedHeader)
        val base = buildEntry()
        val row = "${CsvFormat.toRow(base)},somevalue"
        val parsed = CsvFormat.fromRow(row, extendedHeaders)!!
        assertEquals(base.kcal, parsed.kcal, 0.001)
        assertEquals(base.foodName, parsed.foodName)
    }

    @Test
    fun `toRow and fromRow round-trip preserves sugar fiber salt and mealCategory`() {
        val entry = buildEntry(sugarG = 5.5, fiberG = 2.3, saltG = 1.2, mealCategory = MealCategory.LUNCH)
        val row = CsvFormat.toRow(entry)
        val parsed = CsvFormat.fromRow(row, headers)!!
        assertEquals(entry.sugarG, parsed.sugarG, 0.001)
        assertEquals(entry.fiberG, parsed.fiberG, 0.001)
        assertEquals(entry.saltG, parsed.saltG, 0.001)
        assertEquals(entry.mealCategory, parsed.mealCategory)
    }

    @Test
    fun `toRow and fromRow round-trip preserves tag`() {
        val entry = buildEntry(tag = FoodTag.HEALTHY)
        val parsed = CsvFormat.fromRow(CsvFormat.toRow(entry), headers)!!
        assertEquals(FoodTag.HEALTHY, parsed.tag)
    }

    @Test
    fun `fromRow defaults tag to NONE when column is absent (backward compat)`() {
        // An old export without the tag column must still import, untagged.
        val oldHeader = listOf(
            CsvColumns.DATE, CsvColumns.FOOD_NAME, CsvColumns.BRAND, CsvColumns.AMOUNT_GRAMS,
            CsvColumns.KCAL, CsvColumns.PROTEIN_G, CsvColumns.CARBS_G, CsvColumns.FAT_G,
            CsvColumns.SUGAR_G, CsvColumns.FIBER_G, CsvColumns.SALT_G, CsvColumns.MEAL_CATEGORY,
            CsvColumns.TIMESTAMP_MS,
        ).joinToString(",")
        val oldHeaders = CsvFormat.parseHeaders(oldHeader)
        val row = "2026-05-28,Apfel,,150.0,78.0,0.5,21.0,0.3,2.0,1.5,0.0,SNACK,1748000000000"
        val entry = CsvFormat.fromRow(row, oldHeaders)!!
        assertEquals(FoodTag.NONE, entry.tag)
    }

    @Test
    fun `fromRow maps unknown tag value to NONE`() {
        val row = CsvFormat.toRow(buildEntry(tag = FoodTag.HEALTHY)).replace("HEALTHY", "BOGUS")
        assertEquals(FoodTag.NONE, CsvFormat.fromRow(row, headers)!!.tag)
    }

    private fun buildEntry(
        foodName: String = "Test",
        brand: String? = "TestBrand",
        amountGrams: Double = 100.0,
        sugarG: Double = 0.0,
        fiberG: Double = 0.0,
        saltG: Double = 0.0,
        mealCategory: MealCategory = MealCategory.SNACK,
        tag: FoodTag = FoodTag.NONE,
    ) = FoodEntry(
        foodName = foodName,
        brand = brand,
        amountGrams = amountGrams,
        kcal = 200.0,
        proteinG = 10.0,
        carbsG = 25.0,
        fatG = 8.0,
        sugarG = sugarG,
        fiberG = fiberG,
        saltG = saltG,
        mealCategory = mealCategory,
        tag = tag,
        date = LocalDate.of(2026, 5, 28),
        timestampMs = 1748000000000L,
    )
}
