package dev.antonlammers.macromind.data.backup

import dev.antonlammers.macromind.domain.model.FoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvFormatTest {

    private val headers = CsvFormat.parseHeaders(CsvColumns.HEADER)

    // ── parseLine ────────────────────────────────────────────────────────────

    @Test
    fun `parseLine splits simple row`() {
        val cols = CsvFormat.parseLine("2026-05-28,Apfel,,150.0,78.0,0.5,21.0,0.3,1748000000000")
        assertEquals("2026-05-28", cols[0])
        assertEquals("Apfel", cols[1])
        assertEquals("", cols[2])
        assertEquals("150.0", cols[3])
    }

    @Test
    fun `parseLine handles quoted field with comma`() {
        val cols = CsvFormat.parseLine("2026-05-28,\"Hähnchen, gegrillt\",,200.0,240.0,48.0,0.0,5.0,1748000000000")
        assertEquals("Hähnchen, gegrillt", cols[1])
    }

    @Test
    fun `parseLine handles escaped quote inside quoted field`() {
        val cols = CsvFormat.parseLine("2026-05-28,\"Bio\"\"Joghurt\",,150.0,90.0,6.0,9.0,3.0,1748000000000")
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
        // Only date and food_name present
        val sparseHeaders = mapOf(CsvColumns.DATE to 0, CsvColumns.FOOD_NAME to 1)
        val entry = CsvFormat.fromRow("2026-05-28,Ei", sparseHeaders)!!
        assertEquals(100.0, entry.amountGrams, 0.001)
        assertEquals(0.0, entry.kcal, 0.001)
        assertEquals(LocalDate.parse("2026-05-28"), entry.date)
    }

    @Test
    fun `fromRow with extra columns parses known fields correctly`() {
        val extendedHeader = "${CsvColumns.HEADER},fiber_g"
        val extendedHeaders = CsvFormat.parseHeaders(extendedHeader)
        val base = buildEntry()
        val row = "${CsvFormat.toRow(base)},3.5"
        val parsed = CsvFormat.fromRow(row, extendedHeaders)!!
        assertEquals(base.kcal, parsed.kcal, 0.001)
        assertEquals(base.foodName, parsed.foodName)
    }

    private fun buildEntry(
        foodName: String = "Test",
        brand: String? = "TestBrand",
        amountGrams: Double = 100.0,
    ) = FoodEntry(
        foodName = foodName,
        brand = brand,
        amountGrams = amountGrams,
        kcal = 200.0,
        proteinG = 10.0,
        carbsG = 25.0,
        fatG = 8.0,
        date = LocalDate.of(2026, 5, 28),
        timestampMs = 1748000000000L,
    )
}
