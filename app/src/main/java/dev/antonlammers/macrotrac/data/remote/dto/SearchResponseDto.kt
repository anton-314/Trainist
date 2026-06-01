package dev.antonlammers.macrotrac.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResponseDto(
    @Json(name = "products") val products: List<ProductDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ProductDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "nutriments") val nutriments: NutrimentsDto? = null,
)

@JsonClass(generateAdapter = true)
data class NutrimentsDto(
    @Json(name = "energy-kcal_100g") val kcalPer100g: Double? = null,
    @Json(name = "proteins_100g") val proteinPer100g: Double? = null,
    @Json(name = "carbohydrates_100g") val carbsPer100g: Double? = null,
    @Json(name = "fat_100g") val fatPer100g: Double? = null,
    @Json(name = "sugars_100g") val sugarPer100g: Double? = null,
    @Json(name = "fiber_100g") val fiberPer100g: Double? = null,
    @Json(name = "salt_100g") val saltPer100g: Double? = null,
)
