package dev.antonlammers.trainist.domain.model

/** Standard Mifflin-St Jeor activity multipliers applied to BMR to get TDEE. */
enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9);

    companion object {
        fun parse(raw: String?): ActivityLevel? =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
