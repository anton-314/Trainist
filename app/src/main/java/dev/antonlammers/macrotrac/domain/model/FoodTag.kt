package dev.antonlammers.macrotrac.domain.model

/**
 * Health label a user assigns to a food to track how "clean" their diet is.
 *
 * Stored per food entry and per custom food (so it only has to be set once and is then reused from
 * the history / custom foods). [NONE] is the default — an untagged food is neutral grey and does
 * **not** count as clean. Only [HEALTHY] counts toward the clean-eating target ([isClean]).
 *
 * Persisted by [name] (in Room and in CSV backups); [parse] reads it back defensively so unknown or
 * missing values (e.g. older backups without the column) fall back to [NONE].
 */
enum class FoodTag {
    NONE, HEALTHY, NEUTRAL, UNHEALTHY;

    /** Only fully healthy foods count toward the clean-eating goal. */
    val isClean: Boolean get() = this == HEALTHY

    companion object {
        /** The tags a user can actively pick, in display order. [NONE] is the implicit default. */
        val selectable: List<FoodTag> = listOf(HEALTHY, NEUTRAL, UNHEALTHY)

        fun parse(raw: String?): FoodTag =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } } ?: NONE
    }
}
