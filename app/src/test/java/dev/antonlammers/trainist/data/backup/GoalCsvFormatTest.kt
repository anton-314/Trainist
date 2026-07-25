package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.BmrProfile
import dev.antonlammers.trainist.domain.model.DailyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalCsvFormatTest {

    private val headers = CsvFormat.parseHeaders(GoalCsvFormat.HEADER)

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
        val h = CsvFormat.parseHeaders("kcal,protein_g,carbs_g,fat_g")
        assertEquals(0, h["kcal"])
        assertEquals(1, h["protein_g"])
        assertEquals(2, h["carbs_g"])
        assertEquals(3, h["fat_g"])
    }

    @Test
    fun `toRow and fromRow round-trip preserves target weight`() {
        val goal = DailyGoal(targetWeightKg = 72.5)
        val parsed = GoalCsvFormat.fromRow(GoalCsvFormat.toRow(goal), headers)!!
        assertEquals(72.5, parsed.targetWeightKg!!, 0.001)
    }

    @Test
    fun `fromRow leaves target null when column absent (old export)`() {
        val legacyHeaders = CsvFormat.parseHeaders("kcal,protein_g,carbs_g,fat_g")
        val parsed = GoalCsvFormat.fromRow("2000.0,150.0,250.0,70.0", legacyHeaders)!!
        assertNull(parsed.targetWeightKg)
    }

    @Test
    fun `fromRow leaves target null when value is blank`() {
        val parsed = GoalCsvFormat.fromRow("2000.0,150.0,250.0,70.0,", headers)!!
        assertNull(parsed.targetWeightKg)
    }

    @Test
    fun `toRow and fromRow round-trip preserves the bmr profile`() {
        val profile = BmrProfile(BiologicalSex.FEMALE, ageYears = 28, heightCm = 168.0, activityLevel = ActivityLevel.LIGHT)
        val goal = DailyGoal(bmrProfile = profile)
        val parsed = GoalCsvFormat.fromRow(GoalCsvFormat.toRow(goal), headers)!!
        assertEquals(profile, parsed.bmrProfile)
    }

    @Test
    fun `fromRow leaves bmr profile null when columns absent (old export)`() {
        val legacyHeaders = CsvFormat.parseHeaders("kcal,protein_g,carbs_g,fat_g")
        val parsed = GoalCsvFormat.fromRow("2000.0,150.0,250.0,70.0", legacyHeaders)!!
        assertNull(parsed.bmrProfile)
    }

    @Test
    fun `fromRow leaves bmr profile null when a value is blank`() {
        val parsed = GoalCsvFormat.fromRow("2000.0,150.0,250.0,70.0,,,,,", headers)!!
        assertNull(parsed.bmrProfile)
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
