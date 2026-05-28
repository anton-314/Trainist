package dev.antonlammers.macromind.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProductResponseDto(
    @Json(name = "status") val status: Int = 0,
    @Json(name = "product") val product: ProductDto? = null,
)
