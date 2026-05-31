package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.DailyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalCsvFormatTest {

    private val headers = GoalCsvFormat.parseHeaders(GoalCsvFormat.HEADER)

    @Test
    fun `toRow and fromRow round-trip preserves all fields`() {
        val goal = DailyGoal(kcal = 2200.0, proteinG = 165.0, carbsG = 240.0, fatG = 75.0)
        val row = GoalCsvFormat.toRow(goal)
        val parsed = GoalCsvFormat.fromRow(row, headers)!!

        assertEquals(goal.kcal, parsed.kcal, 0.001)
        assertEquals(goal.proteinG, parsed.proteinG, 0.001)
        assertEquals(goal.carbsG, parsed.carbsG, 0.001)
        assertEquals(goal.fatG, parsed.fatG, 0.001)
    }

    @Test
    fun `fromRow returns null when kcal is missing`() {
        val sparseHeaders = mapOf("protein_g" to 0, "carbs_g" to 1, "fat_g" to 2)
        assertNull(GoalCsvFormat.fromRow("150.0,250.0,70.0", sparseHeaders))
    }

    @Test
    fun `fromRow returns null when any field is missing`() {
        val partialHeaders = mapOf("kcal" to 0, "protein_g" to 1)
        assertNull(GoalCsvFormat.fromRow("2000.0,150.0", partialHeaders))
    }

    @Test
    fun `parseHeaders returns correct index map`() {
        val headers = GoalCsvFormat.parseHeaders("kcal,protein_g,carbs_g,fat_g")
        assertEquals(0, headers["kcal"])
        assertEquals(1, headers["protein_g"])
        assertEquals(2, headers["carbs_g"])
        assertEquals(3, headers["fat_g"])
    }

    @Test
    fun `fromRow handles default DailyGoal values`() {
        val goal = DailyGoal()
        val row = GoalCsvFormat.toRow(goal)
        val parsed = GoalCsvFormat.fromRow(row, headers)!!

        assertEquals(2000.0, parsed.kcal, 0.001)
        assertEquals(150.0, parsed.proteinG, 0.001)
        assertEquals(250.0, parsed.carbsG, 0.001)
        assertEquals(70.0, parsed.fatG, 0.001)
    }
}
