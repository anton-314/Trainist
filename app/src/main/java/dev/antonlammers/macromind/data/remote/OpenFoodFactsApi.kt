package dev.antonlammers.macromind.data.remote

import dev.antonlammers.macromind.data.remote.dto.ProductResponseDto
import dev.antonlammers.macromind.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/search")
    suspend fun search(
        @Query("search_terms") query: String,
        @Query("fields") fields: String = "id,product_name,brands,nutriments",
        @Query("page_size") pageSize: Int = 25,
    ): SearchResponseDto

    @GET("api/v2/product/{barcode}")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "id,product_name,brands,nutriments",
    ): ProductResponseDto
}
