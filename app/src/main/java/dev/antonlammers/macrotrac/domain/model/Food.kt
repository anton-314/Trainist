package dev.antonlammers.macrotrac.domain.model

data class Food(
    val id: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val sugarPer100g: Double = 0.0,
    val fiberPer100g: Double = 0.0,
    val saltPer100g: Double = 0.0,
    val tag: FoodTag = FoodTag.NONE,
)
