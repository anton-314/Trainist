package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.remote.OpenFoodFactsApi
import dev.antonlammers.trainist.data.remote.OpenFoodFactsSearchApi
import dev.antonlammers.trainist.data.remote.SearchLocale
import dev.antonlammers.trainist.data.remote.SearchLocaleProvider
import dev.antonlammers.trainist.data.remote.dto.NutrimentsDto
import dev.antonlammers.trainist.data.remote.dto.ProductResponseDto
import dev.antonlammers.trainist.data.remote.dto.SearchHitDto
import dev.antonlammers.trainist.data.remote.dto.SearchResponseDto
import dev.antonlammers.trainist.domain.model.BarcodeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FoodSearchRepositoryTest {

    /** Records the queries it is asked and answers with canned pages, keyed by query substring. */
    private class FakeSearchApi(
        private val pages: Map<String, List<SearchHitDto>> = emptyMap(),
        private val failWith: Throwable? = null,
    ) : OpenFoodFactsSearchApi {
        val queries = mutableListOf<String>()
        val languages = mutableListOf<String>()

        override suspend fun search(
            query: String,
            langs: String,
            fields: String,
            pageSize: Int,
        ): SearchResponseDto {
            queries += query
            languages += langs
            failWith?.let { throw it }
            val hits = pages.entries.firstOrNull { query.contains(it.key) }?.value
                ?: pages[DEFAULT]?.takeIf { !query.contains("countries_tags") }
                ?: emptyList()
            return SearchResponseDto(hits = hits, count = hits.size)
        }

        companion object {
            const val DEFAULT = "*"
        }
    }

    private object UnusedProductApi : OpenFoodFactsApi {
        override suspend fun getProduct(barcode: String, fields: String): ProductResponseDto =
            throw UnsupportedOperationException("not part of these tests")
    }

    private fun locales(language: String = "de", country: String = "DE") = object : SearchLocaleProvider {
        override fun current() = SearchLocale(language, country)
    }

    private fun repository(searchApi: OpenFoodFactsSearchApi, provider: SearchLocaleProvider = locales()) =
        FoodSearchRepositoryImpl(UnusedProductApi, searchApi, provider)

    private fun hit(code: String, name: String, kcal: Double? = 100.0, brand: String? = null) = SearchHitDto(
        code = code,
        productName = name,
        brands = listOfNotNull(brand),
        nutriments = NutrimentsDto(
            kcalPer100g = kcal,
            proteinPer100g = 3.0,
            carbsPer100g = 5.0,
            fatPer100g = 1.5,
        ),
    )

    private fun manyHits(count: Int, prefix: String) =
        (1..count).map { hit("$prefix$it", "$prefix $it") }

    @Test
    fun `the query carries the country filter and the app language`() = runTest {
        val api = FakeSearchApi(mapOf("countries_tags" to manyHits(5, "de")))
        val repo = repository(api, locales(language = "fr", country = "FR"))

        repo.searchByName("lait")

        assertEquals(listOf("lait AND countries_tags:\"en:france\""), api.queries)
        assertEquals(listOf("fr"), api.languages)
    }

    @Test
    fun `hits are mapped into foods`() = runTest {
        val api = FakeSearchApi(mapOf("countries_tags" to List(5) { hit("40001", "Vollmilch", brand = "Weihenstephan") }))
        val repo = repository(api)

        val foods = repo.searchByName("milch").getOrThrow()

        val first = foods.first()
        assertEquals("40001", first.id)
        assertEquals("Vollmilch", first.name)
        assertEquals("Weihenstephan", first.brand)
        assertEquals(100.0, first.kcalPer100g, 0.001)
        assertEquals(3.0, first.proteinPer100g, 0.001)
    }

    @Test
    fun `hits without calories are dropped`() = runTest {
        // The service returns them, but a product with no energy value cannot become a food entry.
        val api = FakeSearchApi(
            mapOf(
                "countries_tags" to listOf(
                    hit("1", "Mit kcal"),
                    hit("2", "Ohne kcal", kcal = null),
                    hit("3", "Mit kcal auch"),
                    hit("4", "Noch eins"),
                    hit("5", "Und noch eins"),
                ),
            ),
        )
        val repo = repository(api)

        val foods = repo.searchByName("milch").getOrThrow()

        assertEquals(listOf("1", "3", "4", "5"), foods.map { it.id })
    }

    @Test
    fun `a hit without a name is dropped`() = runTest {
        val api = FakeSearchApi(
            mapOf("countries_tags" to (listOf(SearchHitDto(code = "1", productName = " ")) + manyHits(5, "ok"))),
        )
        val repo = repository(api)

        val foods = repo.searchByName("milch").getOrThrow()

        assertTrue(foods.none { it.id == "1" })
    }

    @Test
    fun `a thin country result is topped up with a worldwide search`() = runTest {
        // Only two local hits: rather than show an almost-empty screen, retry without the filter.
        val api = FakeSearchApi(
            mapOf(
                "countries_tags" to manyHits(2, "local"),
                FakeSearchApi.DEFAULT to manyHits(3, "world"),
            ),
        )
        val repo = repository(api)

        val foods = repo.searchByName("quark").getOrThrow()

        assertEquals(2, api.queries.size)
        assertTrue(api.queries[0].contains("countries_tags"))
        assertTrue(!api.queries[1].contains("countries_tags"))
        // The country's own hits stay on top; the worldwide ones only fill the tail.
        assertEquals(listOf("local1", "local2", "world1", "world2", "world3"), foods.map { it.id })
    }

    @Test
    fun `a full country result is not topped up`() = runTest {
        val api = FakeSearchApi(mapOf("countries_tags" to manyHits(5, "local")))
        val repo = repository(api)

        repo.searchByName("milch")

        assertEquals(1, api.queries.size)
    }

    @Test
    fun `a product found in both searches is listed once`() = runTest {
        val api = FakeSearchApi(
            mapOf(
                "countries_tags" to listOf(hit("shared", "Skyr")),
                FakeSearchApi.DEFAULT to listOf(hit("shared", "Skyr"), hit("other", "Skyr Vanille")),
            ),
        )
        val repo = repository(api)

        val foods = repo.searchByName("skyr").getOrThrow()

        assertEquals(listOf("shared", "other"), foods.map { it.id })
    }

    @Test
    fun `an unknown device region searches worldwide without a filter`() = runTest {
        val api = FakeSearchApi(mapOf(FakeSearchApi.DEFAULT to manyHits(2, "world")))
        val repo = repository(api, locales(country = ""))

        val foods = repo.searchByName("milch").getOrThrow()

        // No filter to fall back from, so exactly one request — and no needless retry.
        assertEquals(listOf("milch"), api.queries)
        assertEquals(2, foods.size)
    }

    @Test
    fun `a query that sanitizes away makes no request`() = runTest {
        val api = FakeSearchApi()
        val repo = repository(api)

        val foods = repo.searchByName("  ").getOrThrow()

        assertTrue(api.queries.isEmpty())
        assertTrue(foods.isEmpty())
    }

    @Test
    fun `a network failure fails the result instead of looking like no hits`() = runTest {
        val repo = repository(FakeSearchApi(failWith = IOException("offline")))

        val result = repo.searchByName("milch")

        assertTrue(result.isFailure)
        assertEquals(BarcodeException.NetworkUnavailable, result.exceptionOrNull())
    }

    @Test
    fun `a server failure is reported as such, not as an empty list`() = runTest {
        val repo = repository(FakeSearchApi(failWith = IOException("connection reset")))

        assertTrue(repo.searchByName("milch").isFailure)
    }
}
