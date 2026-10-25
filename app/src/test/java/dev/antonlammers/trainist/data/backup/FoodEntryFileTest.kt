package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.backup.FoodEntryFile
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [parseFoodEntryFile] is the gate for "Öffnen mit Trainist": it must accept the food CSVs an AI
 * assistant writes (including their usual quirks) and reject everything that is not one, because
 * any other backup section would replace data instead of adding to it.
 */
class FoodEntryFileTest {

    private fun parse(text: String) = parseFoodEntryFile(text.toByteArray(Charsets.UTF_8))

    private fun entries(text: String) = parse(text) as FoodEntryFile.Entries

    @Test
    fun `the documented AI format parses into one entry with totals as given`() {
        val file = entries(
            """
            ${CsvColumns.HEADER}
            2026-10-25,"Hähnchenbrust, gebraten",,100,165,31,0,3.6,0,0,0.2,DINNER,HEALTHY,
            """.trimIndent(),
        )

        val entry = file.entries.single()
        assertEquals(0, file.skipped)
        assertEquals(LocalDate.of(2026, 10, 25), entry.date)
        assertEquals("Hähnchenbrust, gebraten", entry.foodName)
        assertNull(entry.brand)
        assertEquals(100.0, entry.amountGrams, 0.0)
        assertEquals(165.0, entry.kcal, 0.0)
        assertEquals(3.6, entry.fatG, 0.0)
        assertEquals(MealCategory.DINNER, entry.mealCategory)
        assertEquals(FoodTag.HEALTHY, entry.tag)
        // An empty timestamp falls back to "now" rather than failing the row.
        assertTrue(entry.timestampMs > 0)
    }

    @Test
    fun `a byte order mark before the header does not hide the date column`() {
        // Left in place, the BOM glues onto "date", that column matches nothing and every row is
        // silently dropped as dateless.
        val file = entries("﻿${CsvColumns.HEADER}\n2026-10-25,Reis,,150,195,4,42,0.4,0,1,0,LUNCH,NEUTRAL,")

        assertEquals("Reis", file.entries.single().foodName)
    }

    @Test
    fun `quoted column names and CRLF line endings are accepted`() {
        val header = CsvColumns.HEADER.split(",").joinToString(",") { "\"$it\"" }
        val file = entries("$header\r\n2026-10-25,Apfel,,180,94,0.5,25,0.3,19,4,0,SNACK,HEALTHY,\r\n")

        assertEquals("Apfel", file.entries.single().foodName)
    }

    @Test
    fun `meal and tag values are case-insensitive`() {
        val entry = entries("${CsvColumns.HEADER}\n2026-10-25,Ei,,60,86,7.5,0.4,6,0,0,0.2,breakfast,healthy,")
            .entries.single()

        assertEquals(MealCategory.BREAKFAST, entry.mealCategory)
        assertEquals(FoodTag.HEALTHY, entry.tag)
    }

    @Test
    fun `malformed rows are counted as skipped while valid ones are kept`() {
        val file = entries(
            """
            ${CsvColumns.HEADER}
            2026-10-25,Quark,,250,170,30,10,0.5,10,0,0.2,DINNER,HEALTHY,
            25.10.2026,Wrong date,,100,100,1,1,1,0,0,0,SNACK,NONE,
            2026-10-25,,,100,100,1,1,1,0,0,0,SNACK,NONE,
            """.trimIndent(),
        )

        assertEquals(listOf("Quark"), file.entries.map { it.foodName })
        assertEquals(2, file.skipped)
    }

    @Test
    fun `a food CSV without any valid row is NoValidRows`() {
        assertEquals(FoodEntryFile.NoValidRows, parse(CsvColumns.HEADER))
        assertEquals(FoodEntryFile.NoValidRows, parse("${CsvColumns.HEADER}\nnot-a-date,X,,1,1,1,1,1,0,0,0,SNACK,NONE,"))
    }

    @Test
    fun `other backup sections are rejected rather than imported`() {
        // Goal and weight CSVs would replace existing values — never allowed through this path.
        assertEquals(FoodEntryFile.NotFoodEntries, parse("${GoalCsvFormat.HEADER}\n2000,150,200,70"))
        assertEquals(FoodEntryFile.NotFoodEntries, parse("${WeightCsvFormat.HEADER}\n2026-10-25,80.0,0"))
        assertEquals(FoodEntryFile.NotFoodEntries, parse("${CustomFoodCsvFormat.HEADER}\n"))
    }

    @Test
    fun `a ZIP backup is rejected by its magic bytes`() {
        val zipLike = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + CsvColumns.HEADER.toByteArray()

        assertEquals(FoodEntryFile.NotFoodEntries, parseFoodEntryFile(zipLike))
    }

    @Test
    fun `empty and unrelated files are rejected`() {
        assertEquals(FoodEntryFile.NotFoodEntries, parse(""))
        assertEquals(FoodEntryFile.NotFoodEntries, parse("\n\n"))
        assertEquals(FoodEntryFile.NotFoodEntries, parse("name,price\nMilch,1.29"))
    }
}
