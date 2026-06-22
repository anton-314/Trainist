package dev.antonlammers.macrotrac.domain.model

data class DailyGoal(
    val kcal: Double = 2000.0,
    val proteinG: Double = 150.0,
    val carbsG: Double = 250.0,
    val fatG: Double = 70.0,
    /** Optional target body weight in kg; `null` means no target set. */
    val targetWeightKg: Double? = null,
)
