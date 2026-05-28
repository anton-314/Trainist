package dev.antonlammers.macromind.domain.model

data class Food(
    val id: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
)
