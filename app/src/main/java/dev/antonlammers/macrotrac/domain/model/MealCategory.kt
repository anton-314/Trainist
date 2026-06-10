package dev.antonlammers.macrotrac.domain.model

enum class MealCategory {
    BREAKFAST, LUNCH, DINNER, SNACK;

    /** Everything except [SNACK] is a "main meal" the day is structured around. */
    val isMainMeal: Boolean get() = this != SNACK

    companion object {
        /** Breakfast, lunch and dinner, in order — the meals eligible for copy-from-yesterday. */
        val mainMeals: List<MealCategory> = entries.filter { it.isMainMeal }
    }
}
