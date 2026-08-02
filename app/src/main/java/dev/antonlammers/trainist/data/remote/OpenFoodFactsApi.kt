package dev.antonlammers.trainist.data.remote

import dev.antonlammers.trainist.data.remote.dto.ProductResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The product endpoint on the main Open Food Facts host, used for barcode lookup only. Full-text
 * search lives on a different host entirely — see [OpenFoodFactsSearchApi].
 */
interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "id,product_name,brands,nutriments",
    ): ProductResponseDto
}
