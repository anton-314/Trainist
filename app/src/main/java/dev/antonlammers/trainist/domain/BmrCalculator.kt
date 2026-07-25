package dev.antonlammers.trainist.domain

import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.WeightGoal

/** Mifflin-St Jeor BMR/TDEE calculation, pure Kotlin (no Android deps). */
object BmrCalculator {
    /** Never recommend eating below this, regardless of goal/formula outcome. */
    const val SAFETY_FLOOR_KCAL = 1200.0

    /** Basal metabolic rate (kcal/day) via the Mifflin-St Jeor formula. */
    fun bmrKcal(sex: BiologicalSex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return if (sex == BiologicalSex.MALE) base + 5.0 else base - 161.0
    }

    /** Total daily energy expenditure = BMR × activity multiplier. */
    fun tdeeKcal(bmrKcal: Double, activityLevel: ActivityLevel): Double = bmrKcal * activityLevel.multiplier

    /**
     * TDEE adjusted by [goal]'s kcal delta, floored so the recommendation never drops below the
     * user's own BMR (resting energy expenditure) nor below [SAFETY_FLOOR_KCAL].
     */
    fun goalKcal(tdeeKcal: Double, bmrKcal: Double, goal: WeightGoal): Double =
        (tdeeKcal + goal.kcalDelta).coerceAtLeast(maxOf(bmrKcal, SAFETY_FLOOR_KCAL))
}
