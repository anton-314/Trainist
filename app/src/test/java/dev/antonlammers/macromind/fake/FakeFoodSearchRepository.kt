package dev.antonlammers.macromind.fake

import dev.antonlammers.macromind.domain.model.Food
import dev.antonlammers.macromind.domain.repository.FoodSearchRepository

class FakeFoodSearchRepository(
    private val searchResult: Result<List<Food>> = Result.success(emptyList()),
    private val barcodeResult: Result<Food?> = Result.success(null),
) : FoodSearchRepository {

    var lastQuery: String? = null
    var lastBarcode: String? = null

    override suspend fun search(query: String): Result<List<Food>> {
        lastQuery = query
        return searchResult
    }

    override suspend fun getByBarcode(barcode: String): Result<Food?> {
        lastBarcode = barcode
        return barcodeResult
    }
}
