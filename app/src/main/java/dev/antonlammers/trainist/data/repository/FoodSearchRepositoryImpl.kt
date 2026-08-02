package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.remote.OffSearchQuery
import dev.antonlammers.trainist.data.remote.OpenFoodFactsApi
import dev.antonlammers.trainist.data.remote.OpenFoodFactsSearchApi
import dev.antonlammers.trainist.data.remote.SearchLocaleProvider
import dev.antonlammers.trainist.domain.model.BarcodeException
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.repository.FoodSearchRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class FoodSearchRepositoryImpl @Inject constructor(
    private val api: OpenFoodFactsApi,
    private val searchApi: OpenFoodFactsSearchApi,
    private val locales: SearchLocaleProvider,
) : FoodSearchRepository {

    /**
     * Name search against Open Food Facts, filtered to the user's country so the results are products
     * they can actually buy (see [OffSearchQuery] for why that filter carries the whole feature).
     *
     * Two adjustments happen around the raw call:
     * - **Fallback for thin regions.** A country whose products are sparsely covered would otherwise
     *   return almost nothing, so a filtered search that comes back nearly empty is retried
     *   unfiltered — a few foreign hits beat an empty screen, but only once the local ones run out.
     * - **Hits without calories are dropped here, not in the query.** `states_tags:"…nutrition-facts-
     *   completed"` was measured to change nothing (17 of 20 hits carried kcal either way), and a
     *   product with no energy value cannot become a food entry.
     */
    override suspend fun searchByName(query: String): Result<List<Food>> = runCatching {
        val locale = locales.current()
        val countryTag = OffSearchQuery.countryTag(locale.countryCode)
        val localResults = runSearch(OffSearchQuery.build(query, countryTag), locale.language)
        if (countryTag == null || localResults.size >= MIN_LOCAL_RESULTS) {
            localResults
        } else {
            // Keep the country's own hits on top; the unfiltered ones only fill up the tail.
            val codes = localResults.mapTo(mutableSetOf()) { it.id }
            localResults + runSearch(OffSearchQuery.build(query, null), locale.language)
                .filterNot { it.id in codes }
        }
    }

    private suspend fun runSearch(query: String?, language: String): List<Food> {
        if (query == null) return emptyList()
        val response = try {
            searchApi.search(query = query, langs = language)
        } catch (e: HttpException) {
            if (e.code() in 400..499) return emptyList()
            throw BarcodeException.ServerUnavailable
        } catch (e: IOException) {
            throw BarcodeException.NetworkUnavailable
        }
        return response.hits.mapNotNull { hit ->
            val name = hit.productName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kcal = hit.nutriments?.kcalPer100g ?: return@mapNotNull null
            Food(
                id = hit.code ?: name,
                name = name,
                brand = hit.brands.firstOrNull()?.takeIf { it.isNotBlank() },
                kcalPer100g = kcal,
                proteinPer100g = hit.nutriments.proteinPer100g ?: 0.0,
                carbsPer100g = hit.nutriments.carbsPer100g ?: 0.0,
                fatPer100g = hit.nutriments.fatPer100g ?: 0.0,
                sugarPer100g = hit.nutriments.sugarPer100g ?: 0.0,
                fiberPer100g = hit.nutriments.fiberPer100g ?: 0.0,
                saltPer100g = hit.nutriments.saltPer100g ?: 0.0,
            )
        }
    }

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

    private companion object {
        /** Below this, a country-filtered search is treated as too thin and retried worldwide. */
        const val MIN_LOCAL_RESULTS = 5
    }
}
