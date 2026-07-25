package dev.antonlammers.trainist.domain.model

/**
 * One reorderable card on the Stats screen. Persisted (as an ordered list of [name]s) via
 * [dev.antonlammers.trainist.domain.repository.SettingsRepository] so the user's chosen card
 * order survives app restarts; [parse] reads it back defensively so unknown/removed values are
 * dropped rather than crashing.
 */
enum class StatCardType {
    CALORIES, CLEAN_EATING, WEIGHT, TRAINING_FREQUENCY, STRENGTH, PROGRESSIVE_OVERLOAD;

    companion object {
        /**
         * Shown the first time the app runs, before any custom order has been saved — deliberately
         * spelled out instead of derived from [entries], so the display order stays a product
         * decision independent of the declaration order (which the persisted names depend on).
         */
        val DEFAULT_ORDER: List<StatCardType> = listOf(
            WEIGHT,
            PROGRESSIVE_OVERLOAD,
            CALORIES,
            CLEAN_EATING,
            STRENGTH,
            TRAINING_FREQUENCY,
        )

        fun parse(raw: String?): StatCardType? =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
