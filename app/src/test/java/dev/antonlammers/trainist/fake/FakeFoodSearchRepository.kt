package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.repository.FoodSearchRepository

class FakeFoodSearchRepository(
    private val barcodeResult: Result<Food?> = Result.success(null),
    private var searchResult: Result<List<Food>> = Result.success(emptyList()),
) : FoodSearchRepository {

    var lastBarcode: String? = null

    /** Every name search this fake has been asked, in order — so a test can assert on debouncing. */
    val searchedQueries = mutableListOf<String>()

    override suspend fun getByBarcode(barcode: String): Result<Food?> {
        lastBarcode = barcode
        return barcodeResult
    }

    override suspend fun searchByName(query: String): Result<List<Food>> {
        searchedQueries += query
        return searchResult
    }

    fun respondWith(result: Result<List<Food>>) {
        searchResult = result
    }
}
