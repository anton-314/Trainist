package dev.antonlammers.macromind.data.repository

import dev.antonlammers.macromind.data.remote.OpenFoodFactsApi
import dev.antonlammers.macromind.domain.model.Food
import dev.antonlammers.macromind.domain.repository.FoodSearchRepository
import javax.inject.Inject

class FoodSearchRepositoryImpl @Inject constructor(
    private val api: OpenFoodFactsApi,
) : FoodSearchRepository {

    override suspend fun getByBarcode(barcode: String): Result<Food?> = runCatching {
        val response = api.getProduct(barcode)
        if (response.status != 1) return@runCatching null
        val dto = response.product ?: return@runCatching null
        val name = dto.productName?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val nutriments = dto.nutriments ?: return@runCatching null
        Food(
            id = dto.id ?: barcode,
            name = name,
            brand = dto.brands?.takeIf { it.isNotBlank() },
            kcalPer100g = nutriments.kcalPer100g ?: 0.0,
            proteinPer100g = nutriments.proteinPer100g ?: 0.0,
            carbsPer100g = nutriments.carbsPer100g ?: 0.0,
            fatPer100g = nutriments.fatPer100g ?: 0.0,
        )
    }

    override suspend fun search(query: String): Result<List<Food>> = runCatching {
        api.search(query).products.mapNotNull { dto ->
            val name = dto.productName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nutriments = dto.nutriments ?: return@mapNotNull null
            Food(
                id = dto.id ?: name,
                name = name,
                brand = dto.brands?.takeIf { it.isNotBlank() },
                kcalPer100g = nutriments.kcalPer100g ?: 0.0,
                proteinPer100g = nutriments.proteinPer100g ?: 0.0,
                carbsPer100g = nutriments.carbsPer100g ?: 0.0,
                fatPer100g = nutriments.fatPer100g ?: 0.0,
            )
        }
    }
}
