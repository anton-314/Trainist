package dev.antonlammers.trainist.domain.model

/**
 * A combination of foods the user logs together ("Meine Mahlzeiten") — the same muesli of oats,
 * quark and milk every morning, logged in one step instead of three searches.
 *
 * Each ingredient is a **snapshot** ([SavedMealItem.food] carries its own per-100 g values), never a
 * reference to a custom food or a product: editing or deleting the custom food later leaves the meal
 * exactly as it was saved — the same stability rule [FoodEntry] follows. Logging a meal expands it
 * into one ordinary [FoodEntry] per ingredient, so nothing downstream knows meals exist.
 *
 * [stableId] (a UUID) is the backup-stable key — re-importing a backup replaces a meal instead of
 * duplicating it. [id] is the Room row id (0 = not yet stored).
 */
data class SavedMeal(
    val id: Long = 0,
    val stableId: String,
    val name: String,
    val items: List<SavedMealItem>,
)

/** One ingredient: the food's per-100 g snapshot (incl. its [FoodTag]) and the planned amount. */
data class SavedMealItem(
    val food: Food,
    val amountGrams: Double,
)
