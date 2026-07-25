package dev.antonlammers.trainist.domain.model

/**
 * The BMR calculator's persisted profile inputs, so reopening the calculator later prefills these
 * instead of starting blank. Body weight is deliberately excluded — it already has proper
 * day-by-day tracking via [WeightEntry], so the calculator prefills weight live from the most
 * recent logged entry instead of freezing a stale snapshot here.
 */
data class BmrProfile(
    val sex: BiologicalSex,
    val ageYears: Int,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
) {
    companion object {
        /** All-or-nothing assembly from nullable parts — a half-saved profile is not a profile. */
        fun fromParts(
            sex: BiologicalSex?,
            ageYears: Int?,
            heightCm: Double?,
            activityLevel: ActivityLevel?,
        ): BmrProfile? =
            if (sex != null && ageYears != null && heightCm != null && activityLevel != null) {
                BmrProfile(sex, ageYears, heightCm, activityLevel)
            } else {
                null
            }
    }
}
