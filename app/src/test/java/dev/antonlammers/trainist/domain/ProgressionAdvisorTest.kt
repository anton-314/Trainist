package dev.antonlammers.trainist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionAdvisorTest {

    /** A window that clears the minimum-evidence bar, so single facts can be varied in isolation. */
    private fun facts(
        trainingWeeks: Int = 6,
        sessions: Int = 18,
        totalChangePercent: Double? = 0.0,
        recentChangePercent: Double? = null,
        bodyWeightTrendKgPerWeek: Double? = null,
        kcalVsGoal: Double? = null,
        proteinGPerKgBodyWeight: Double? = null,
        sessionsPerWeek: Double = 3.0,
    ) = ProgressionFacts(
        trainingWeeks = trainingWeeks,
        sessions = sessions,
        totalChangePercent = totalChangePercent,
        recentChangePercent = recentChangePercent,
        bodyWeightTrendKgPerWeek = bodyWeightTrendKgPerWeek,
        kcalVsGoal = kcalVsGoal,
        proteinGPerKgBodyWeight = proteinGPerKgBodyWeight,
        sessionsPerWeek = sessionsPerWeek,
    )

    // --- The "don't jump to conclusions" guard ------------------------------------------------

    @Test
    fun `a single week of data yields no verdict and no causal advice`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(trainingWeeks = 1, sessions = 2, totalChangePercent = -12.0, bodyWeightTrendKgPerWeek = -1.5),
        )

        assertEquals(ProgressionTrend.INSUFFICIENT_DATA, insight.trend)
        assertEquals(listOf(ProgressionAdvice.COLLECT_MORE_DATA), insight.advice)
    }

    @Test
    fun `enough weeks but too few sessions is still insufficient`() {
        val insight = ProgressionAdvisor.evaluate(facts(trainingWeeks = 4, sessions = 5, totalChangePercent = 8.0))

        assertEquals(ProgressionTrend.INSUFFICIENT_DATA, insight.trend)
        assertEquals(listOf(ProgressionAdvice.COLLECT_MORE_DATA), insight.advice)
    }

    @Test
    fun `a missing index change is never interpreted`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = null))

        assertEquals(ProgressionTrend.INSUFFICIENT_DATA, insight.trend)
    }

    @Test
    fun `exactly at the minimum evidence bar a verdict is given`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(
                trainingWeeks = ProgressionAdvisor.MIN_TRAINING_WEEKS,
                sessions = ProgressionAdvisor.MIN_SESSIONS,
                totalChangePercent = 5.0,
            ),
        )

        assertEquals(ProgressionTrend.PROGRESSING, insight.trend)
    }

    // --- Trend classification ------------------------------------------------------------------

    @Test
    fun `change inside the dead band counts as maintaining`() {
        assertEquals(ProgressionTrend.MAINTAINING, ProgressionAdvisor.evaluate(facts(totalChangePercent = 1.9)).trend)
        assertEquals(ProgressionTrend.MAINTAINING, ProgressionAdvisor.evaluate(facts(totalChangePercent = -2.9)).trend)
    }

    @Test
    fun `the decline threshold is wider than the progress threshold`() {
        assertEquals(ProgressionTrend.PROGRESSING, ProgressionAdvisor.evaluate(facts(totalChangePercent = 2.0)).trend)
        // The mirrored -2.0 is deliberately *not* a decline yet.
        assertEquals(ProgressionTrend.MAINTAINING, ProgressionAdvisor.evaluate(facts(totalChangePercent = -2.0)).trend)
        assertEquals(ProgressionTrend.DECLINING, ProgressionAdvisor.evaluate(facts(totalChangePercent = -3.0)).trend)
    }

    // --- Advice for a rising index ---------------------------------------------------------------

    @Test
    fun `progressing leads with keep going`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = 6.0))

        assertEquals(listOf(ProgressionAdvice.KEEP_GOING), insight.advice)
    }

    @Test
    fun `progressing overall but flattening recently adds the stall hint`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = 6.0, recentChangePercent = -0.4))

        assertEquals(
            listOf(ProgressionAdvice.KEEP_GOING, ProgressionAdvice.RECENT_STALL),
            insight.advice,
        )
    }

    @Test
    fun `low protein is flagged even while progressing`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = 6.0, proteinGPerKgBodyWeight = 1.1))

        assertTrue(ProgressionAdvice.PROTEIN_LOW in insight.advice)
    }

    @Test
    fun `progressing never suggests eating more or deloading`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(trainingWeeks = 12, totalChangePercent = 9.0, bodyWeightTrendKgPerWeek = -0.6),
        )

        assertFalse(ProgressionAdvice.ENERGY_DEFICIT in insight.advice)
        assertFalse(ProgressionAdvice.DELOAD in insight.advice)
    }

    // --- Advice for a flat or falling index ------------------------------------------------------

    @Test
    fun `declining while losing weight blames the energy deficit first`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(totalChangePercent = -6.0, bodyWeightTrendKgPerWeek = -0.7),
        )

        assertEquals(ProgressionTrend.DECLINING, insight.trend)
        assertEquals(ProgressionAdvice.ENERGY_DEFICIT, insight.advice.first())
    }

    @Test
    fun `maintaining while losing weight is framed as a success, not a deficit problem`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(totalChangePercent = 0.5, bodyWeightTrendKgPerWeek = -0.7),
        )

        assertEquals(ProgressionTrend.MAINTAINING, insight.trend)
        assertEquals(ProgressionAdvice.EXPECTED_IN_DEFICIT, insight.advice.first())
        assertFalse(ProgressionAdvice.ENERGY_DEFICIT in insight.advice)
    }

    @Test
    fun `a measured stable body weight overrides low logged kcal`() {
        // Logged intake looks low, but the scale says energy balance is fine — trust the outcome.
        val insight = ProgressionAdvisor.evaluate(
            facts(totalChangePercent = 0.0, bodyWeightTrendKgPerWeek = 0.0, kcalVsGoal = 0.5),
        )

        assertFalse(ProgressionAdvice.ENERGY_DEFICIT in insight.advice)
        assertFalse(ProgressionAdvice.EXPECTED_IN_DEFICIT in insight.advice)
    }

    @Test
    fun `logged kcal is used when no body-weight trend exists`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(totalChangePercent = -6.0, bodyWeightTrendKgPerWeek = null, kcalVsGoal = 0.7),
        )

        assertEquals(ProgressionAdvice.ENERGY_DEFICIT, insight.advice.first())
    }

    @Test
    fun `sparse training is called out as a consistency problem`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = 0.0, sessionsPerWeek = 1.0))

        assertTrue(ProgressionAdvice.CONSISTENCY in insight.advice)
        assertFalse(ProgressionAdvice.RECOVERY_FREQUENCY in insight.advice)
    }

    @Test
    fun `very frequent training points at recovery`() {
        val insight = ProgressionAdvisor.evaluate(facts(totalChangePercent = 0.0, sessionsPerWeek = 6.0))

        assertTrue(ProgressionAdvice.RECOVERY_FREQUENCY in insight.advice)
        assertFalse(ProgressionAdvice.CONSISTENCY in insight.advice)
    }

    @Test
    fun `a long accumulation block suggests a deload`() {
        val insight = ProgressionAdvisor.evaluate(facts(trainingWeeks = 10, totalChangePercent = 0.0))

        assertTrue(ProgressionAdvice.DELOAD in insight.advice)
    }

    @Test
    fun `stagnating with everything else fine falls back to programming advice`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(
                trainingWeeks = 5,
                totalChangePercent = 0.0,
                bodyWeightTrendKgPerWeek = 0.1,
                proteinGPerKgBodyWeight = 2.0,
                sessionsPerWeek = 3.0,
            ),
        )

        assertEquals(listOf(ProgressionAdvice.PROGRESSION_SCHEME), insight.advice)
    }

    @Test
    fun `advice is capped and keeps the highest-ranked causes`() {
        val insight = ProgressionAdvisor.evaluate(
            facts(
                trainingWeeks = 12,
                totalChangePercent = -8.0,
                bodyWeightTrendKgPerWeek = -0.9,
                proteinGPerKgBodyWeight = 1.0,
                sessionsPerWeek = 6.0,
            ),
        )

        assertEquals(ProgressionAdvisor.MAX_ADVICE, insight.advice.size)
        assertEquals(
            listOf(
                ProgressionAdvice.ENERGY_DEFICIT,
                ProgressionAdvice.RECOVERY_FREQUENCY,
                ProgressionAdvice.PROTEIN_LOW,
            ),
            insight.advice,
        )
    }
}
