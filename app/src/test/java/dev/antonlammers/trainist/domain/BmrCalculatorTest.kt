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
    fun `goalDeltaKcal LOSE takes a fraction of tdee`() {
        // 2759 × −0.18 = −496.6, inside the −700…−250 guard rails.
        assertEquals(-496.62, BmrCalculator.goalDeltaKcal(tdeeKcal = 2759.0, WeightGoal.LOSE), 0.001)
        assertEquals(2262.38, BmrCalculator.goalKcal(tdeeKcal = 2759.0, bmrKcal = 1780.0, WeightGoal.LOSE), 0.001)
    }

    @Test
    fun `goalDeltaKcal GAIN takes a fraction of tdee`() {
        // 2759 × 0.10 = 275.9, inside the 150…400 guard rails.
        assertEquals(275.9, BmrCalculator.goalDeltaKcal(tdeeKcal = 2759.0, WeightGoal.GAIN), 0.001)
        assertEquals(3034.9, BmrCalculator.goalKcal(tdeeKcal = 2759.0, bmrKcal = 1780.0, WeightGoal.GAIN), 0.001)
    }

    @Test
    fun `the deficit scales with body size instead of being flat`() {
        // The point of the fraction: a small person is not put on the same absolute cut as a large
        // one. 1750 kcal TDEE → −315, 3000 → −540 (both inside the guard rails).
        assertEquals(-315.0, BmrCalculator.goalDeltaKcal(1750.0, WeightGoal.LOSE), 0.001)
        assertEquals(-540.0, BmrCalculator.goalDeltaKcal(3000.0, WeightGoal.LOSE), 0.001)
    }

    @Test
    fun `goalDeltaKcal is clamped at both ends`() {
        // Very large TDEE: 5000 × −0.18 = −900 → capped to the −700 guard rail.
        assertEquals(-700.0, BmrCalculator.goalDeltaKcal(5000.0, WeightGoal.LOSE), 0.001)
        // Very small TDEE: 1200 × −0.18 = −216 → raised to the −250 minimum, so the change stays
        // distinguishable from day-to-day weight noise.
        assertEquals(-250.0, BmrCalculator.goalDeltaKcal(1200.0, WeightGoal.LOSE), 0.001)
        assertEquals(400.0, BmrCalculator.goalDeltaKcal(5000.0, WeightGoal.GAIN), 0.001)
        assertEquals(150.0, BmrCalculator.goalDeltaKcal(1200.0, WeightGoal.GAIN), 0.001)
    }

    @Test
    fun `goalDeltaKcal MAINTAIN is zero at any tdee`() {
        assertEquals(0.0, BmrCalculator.goalDeltaKcal(1200.0, WeightGoal.MAINTAIN), 0.001)
        assertEquals(0.0, BmrCalculator.goalDeltaKcal(5000.0, WeightGoal.MAINTAIN), 0.001)
    }

    @Test
    fun `goalKcal LOSE is floored at bmr when the deficit would drop below it`() {
        // Sedentary female: bmr 1345.25, tdee 1614.3, deficit −290.6 → 1323.7 < bmr → floored.
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

    @Test
    fun `weeklyWeightChangeKg converts the daily delta into kg per week`() {
        // −500 kcal/day × 7 ÷ 7700 = −0.4545 kg/week.
        assertEquals(-0.4545, BmrCalculator.weeklyWeightChangeKg(tdeeKcal = 2500.0, goalKcal = 2000.0), 0.001)
        assertEquals(0.2727, BmrCalculator.weeklyWeightChangeKg(tdeeKcal = 2500.0, goalKcal = 2800.0), 0.001)
        assertEquals(0.0, BmrCalculator.weeklyWeightChangeKg(tdeeKcal = 2500.0, goalKcal = 2500.0), 0.001)
    }

    @Test
    fun `weeklyWeightChangeKg reports the floored target, not the nominal deficit`() {
        // The floor raises the recommendation back to BMR, so the honest rate is the smaller one.
        val bmr = 1345.25
        val tdee = BmrCalculator.tdeeKcal(bmr, ActivityLevel.SEDENTARY)
        val kcal = BmrCalculator.goalKcal(tdee, bmr, WeightGoal.LOSE)
        val expected = (bmr - tdee) * 7.0 / BmrCalculator.KCAL_PER_KG_BODY_WEIGHT

        assertEquals(expected, BmrCalculator.weeklyWeightChangeKg(tdee, kcal), 0.001)
        assertTrue(BmrCalculator.weeklyWeightChangeKg(tdee, kcal) > -0.25)
    }
}
