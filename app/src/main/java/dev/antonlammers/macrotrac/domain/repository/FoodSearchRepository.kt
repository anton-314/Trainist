package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.Food

interface FoodSearchRepository {
    suspend fun search(query: String): Result<List<Food>>
    suspend fun getByBarcode(barcode: String): Result<Food?>
}
