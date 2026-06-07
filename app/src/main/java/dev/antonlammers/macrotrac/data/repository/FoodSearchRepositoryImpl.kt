package dev.antonlammers.macrotrac.data.repository

import dev.antonlammers.macrotrac.data.remote.OpenFoodFactsApi
import dev.antonlammers.macrotrac.domain.model.BarcodeException
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.repository.FoodSearchRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class FoodSearchRepositoryImpl @Inject constructor(
    private val api: OpenFoodFactsApi,
) : FoodSearchRepository {

    override suspend fun getByBarcode(barcode: String): Result<Food?> = runCatching {
        val response = try {
            api.getProduct(barcode)
        } catch (e: HttpException) {
            if (e.code() in 400..499) return@runCatching null
            throw BarcodeException.ServerUnavailable
        } catch (e: IOException) {
            throw BarcodeException.NetworkUnavailable
        }
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
            sugarPer100g = nutriments.sugarPer100g ?: 0.0,
            fiberPer100g = nutriments.fiberPer100g ?: 0.0,
            saltPer100g = nutriments.saltPer100g ?: 0.0,
        )
    }
}
