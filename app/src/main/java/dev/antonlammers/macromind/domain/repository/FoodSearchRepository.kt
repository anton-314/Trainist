package dev.antonlammers.macromind.domain.repository

import dev.antonlammers.macromind.domain.model.Food

interface FoodSearchRepository {
    suspend fun search(query: String): Result<List<Food>>
    suspend fun getByBarcode(barcode: String): Result<Food?>
}
