package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmrCalculatorTest {

    @Test
    fun `bmrKcal male reference case`() {
        // 10×80 + 6.25×180 − 5×30 + 5 = 800 + 1125 − 150 + 5 = 1780
        val bmr = BmrCalculator.bmrKcal(BiologicalSex.MALE, weightKg = 80.0, heightCm = 180.0, ageYears = 30)
        assertEquals(1780.0, bmr, 0.001)
    }

    @Test
    fun `bmrKcal female reference case`() {
        // 10×60 + 6.25×165 − 5×25 − 161 = 600 + 1031.25 − 125 − 161 = 1345.25
        val bmr = BmrCalculator.bmrKcal(BiologicalSex.FEMALE, weightKg = 60.0, heightCm = 165.0, ageYears = 25)
        assertEquals(1345.25, bmr, 0.001)
    }

    @Test
    fun `bmrKcal decreases as age increases`() {
        val younger = BmrCalculator.bmrKcal(BiologicalSex.MALE, 80.0, 180.0, 30)
        val older = BmrCalculator.bmrKcal(BiologicalSex.MALE, 80.0, 180.0, 31)
        assertTrue(older < younger)
        assertEquals(5.0, younger - older, 0.001)
    }

    @Test
    fun `tdeeKcal applies each activity multiplier`() {
        val bmr = 1780.0
        assertEquals(2136.0, BmrCalculator.tdeeKcal(bmr, ActivityLevel.SEDENTARY), 0.001)
        assertEquals(2447.5, BmrCalculator.tdeeKcal(bmr, ActivityLevel.LIGHT), 0.001)
        assertEquals(2759.0, BmrCalculator.tdeeKcal(bmr, ActivityLevel.MODERATE), 0.001)
        assertEquals(3070.5, BmrCalculator.tdeeKcal(bmr, ActivityLevel.ACTIVE), 0.001)
        assertEquals(3382.0, BmrCalculator.tdeeKcal(bmr, ActivityLevel.VERY_ACTIVE), 0.001)
    }

    @Test
    fun `goalKcal MAINTAIN returns tdee unchanged`() {
        assertEquals(2759.0, BmrCalculator.goalKcal(tdeeKcal = 2759.0, bmrKcal = 1780.0, WeightGoal.MAINTAIN), 0.001)
    }

    @Test
    fun `goalKcal LOSE subtracts 500 when above the floor`() {
        assertEquals(2259.0, BmrCalculator.goalKcal(tdeeKcal = 2759.0, bmrKcal = 1780.0, WeightGoal.LOSE), 0.001)
    }

    @Test
    fun `goalKcal GAIN adds 350`() {
        assertEquals(3109.0, BmrCalculator.goalKcal(tdeeKcal = 2759.0, bmrKcal = 1780.0, WeightGoal.GAIN), 0.001)
    }

    @Test
    fun `goalKcal LOSE is floored at bmr when naive subtraction would drop below it`() {
        // Sedentary female: bmr 1345.25, tdee 1614.3, naive LOSE 1114.3 < bmr → floored to bmr.
        val bmr = 1345.25
        val tdee = BmrCalculator.tdeeKcal(bmr, ActivityLevel.SEDENTARY)
        assertEquals(1614.3, tdee, 0.001)
        assertEquals(bmr, BmrCalculator.goalKcal(tdee, bmr, WeightGoal.LOSE), 0.001)
    }

    @Test
    fun `goalKcal never drops below the absolute safety floor`() {
        val result = BmrCalculator.goalKcal(tdeeKcal = 1000.0, bmrKcal = 900.0, WeightGoal.LOSE)
        assertEquals(BmrCalculator.SAFETY_FLOOR_KCAL, result, 0.001)
    }
}
