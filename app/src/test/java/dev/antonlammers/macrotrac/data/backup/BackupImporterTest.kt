package dev.antonlammers.macrotrac.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImporterTest {

    @Test
    fun `detectCsvType returns FOOD_ENTRIES when food_name column is present`() {
        val headers = CsvFormat.parseHeaders(CsvColumns.HEADER)
        assertEquals(CsvType.FOOD_ENTRIES, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns WEIGHT_ENTRIES when weight_kg column is present`() {
        val headers = CsvFormat.parseHeaders(WeightCsvFormat.HEADER)
        assertEquals(CsvType.WEIGHT_ENTRIES, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns DAILY_GOAL when kcal is present but no food_name or weight_kg`() {
        val headers = CsvFormat.parseHeaders(GoalCsvFormat.HEADER)
        assertEquals(CsvType.DAILY_GOAL, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns CUSTOM_FOODS when kcal_per_100g column is present`() {
        val headers = CsvFormat.parseHeaders(CustomFoodCsvFormat.HEADER)
        assertEquals(CsvType.CUSTOM_FOODS, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns UNKNOWN for unrecognised headers`() {
        val headers = CsvFormat.parseHeaders("foo,bar,baz")
        assertEquals(CsvType.UNKNOWN, detectCsvType(headers))
    }

    @Test
    fun `detectCsvType returns FOOD_ENTRIES for old export without salt_g`() {
        val oldHeader = listOf(
            CsvColumns.DATE, CsvColumns.FOOD_NAME, CsvColumns.BRAND, CsvColumns.AMOUNT_GRAMS,
            CsvColumns.KCAL, CsvColumns.PROTEIN_G, CsvColumns.CARBS_G, CsvColumns.FAT_G,
            CsvColumns.SUGAR_G, CsvColumns.FIBER_G, CsvColumns.MEAL_CATEGORY, CsvColumns.TIMESTAMP_MS,
        ).joinToString(",")
        assertEquals(CsvType.FOOD_ENTRIES, detectCsvType(CsvFormat.parseHeaders(oldHeader)))
    }
}
