package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.WeightGoal

/** Mifflin-St Jeor BMR/TDEE calculation, pure Kotlin (no Android deps). */
object BmrCalculator {
    /** Never recommend eating below this, regardless of goal/formula outcome. */
    const val SAFETY_FLOOR_KCAL = 1200.0

    /**
     * Energy stored in a kilogram of body tissue, used to turn a daily kcal delta into an expected
     * weekly weight change (~7700 kcal/kg, the metric form of the "3500 kcal per pound" rule).
     * The rule is an approximation that **overestimates** long-term loss — expenditure falls as
     * weight does — so treat the derived rate as an order of magnitude for the first weeks, not a
     * promise. It exists to make the chosen goal's aggressiveness legible.
     */
    const val KCAL_PER_KG_BODY_WEIGHT = 7700.0

    /** Basal metabolic rate (kcal/day) via the Mifflin-St Jeor formula. */
    fun bmrKcal(sex: BiologicalSex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return if (sex == BiologicalSex.MALE) base + 5.0 else base - 161.0
    }

    /** Total daily energy expenditure = BMR × activity multiplier. */
    fun tdeeKcal(bmrKcal: Double, activityLevel: ActivityLevel): Double = bmrKcal * activityLevel.multiplier

    /**
     * The daily deviation from TDEE for [goal] — [WeightGoal.tdeeFraction] of the user's own TDEE,
     * held inside the goal's guard rails so neither a very large nor a very small TDEE produces an
     * unreasonable target. Negative for a deficit, positive for a surplus.
     */
    fun goalDeltaKcal(tdeeKcal: Double, goal: WeightGoal): Double =
        (tdeeKcal * goal.tdeeFraction).coerceIn(goal.minDeltaKcal, goal.maxDeltaKcal)

    /**
     * TDEE adjusted by [goalDeltaKcal], floored so the recommendation never drops below the user's
     * own BMR (resting energy expenditure) nor below [SAFETY_FLOOR_KCAL].
     */
    fun goalKcal(tdeeKcal: Double, bmrKcal: Double, goal: WeightGoal): Double =
        (tdeeKcal + goalDeltaKcal(tdeeKcal, goal)).coerceAtLeast(maxOf(bmrKcal, SAFETY_FLOOR_KCAL))

    /**
     * Expected weekly weight change (kg, signed) for a recommendation of [goalKcal] against
     * [tdeeKcal]. Derived from the *final* recommendation rather than the goal's nominal delta, so
     * a target raised by the BMR/safety floor reports the smaller change it will actually produce.
     */
    fun weeklyWeightChangeKg(tdeeKcal: Double, goalKcal: Double): Double =
        (goalKcal - tdeeKcal) * DAYS_PER_WEEK / KCAL_PER_KG_BODY_WEIGHT

    private const val DAYS_PER_WEEK = 7.0
}
