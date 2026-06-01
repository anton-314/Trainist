package dev.antonlammers.macrotrac.domain.model

import java.time.LocalDate

data class FoodEntry(
    val id: Long = 0,
    val foodName: String,
    val brand: String?,
    val amountGrams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val sugarG: Double = 0.0,
    val fiberG: Double = 0.0,
    val saltG: Double = 0.0,
    val mealCategory: MealCategory = MealCategory.SNACK,
    val date: LocalDate,
    val timestampMs: Long,
)
