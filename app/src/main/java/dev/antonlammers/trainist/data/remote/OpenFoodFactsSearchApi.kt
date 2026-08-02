package dev.antonlammers.trainist.data.remote

import dev.antonlammers.trainist.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Food Facts' full-text search, which lives on its **own host** (`search.openfoodfacts.org`) and
 * therefore gets its own Retrofit instance — see `NetworkModule`.
 *
 * The old endpoints on the main site are not an option: both `cgi/search.pl` and `api/v2/search`
 * answer with an HTML "page temporarily unavailable" placeholder rather than JSON.
 *
 * Deliberately **no `sort_by`**: the default is relevance, and any explicit sort replaces it — sorting
 * by scan count returns the most popular products that match anywhere, not the ones the user meant.
 */
interface OpenFoodFactsSearchApi {

    @GET("search")
    suspend fun search(
        /** Lucene query, built by [OffSearchQuery] (terms + optional country filter). */
        @Query("q") query: String,
        /** The user's language; measurably improves ranking on top of the country filter. */
        @Query("langs") langs: String,
        @Query("fields") fields: String = FIELDS,
        @Query("page_size") pageSize: Int = PAGE_SIZE,
    ): SearchResponseDto

    companion object {
        const val FIELDS = "code,product_name,brands,quantity,nutriments"
        const val PAGE_SIZE = 25
    }
}
