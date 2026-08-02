package dev.antonlammers.trainist.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A page of results from Open Food Facts' search service (`search.openfoodfacts.org`).
 *
 * Note the shape differs from the product endpoint's: results arrive under `hits`, the barcode is
 * `code` rather than `id`, and `brands` is a **list** instead of a comma-separated string — which is
 * why this has its own hit DTO rather than reusing [ProductDto].
 */
@JsonClass(generateAdapter = true)
data class SearchResponseDto(
    @Json(name = "hits") val hits: List<SearchHitDto> = emptyList(),
    @Json(name = "count") val count: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SearchHitDto(
    @Json(name = "code") val code: String? = null,
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: List<String> = emptyList(),
    @Json(name = "quantity") val quantity: String? = null,
    @Json(name = "nutriments") val nutriments: NutrimentsDto? = null,
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
