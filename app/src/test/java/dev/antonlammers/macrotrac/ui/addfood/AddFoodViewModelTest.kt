package dev.antonlammers.macrotrac.ui.addfood

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.fake.FakeCustomFoodRepository
import dev.antonlammers.macrotrac.fake.FakeFoodEntryRepository
import dev.antonlammers.macrotrac.fake.FakeFoodSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var searchRepo: FakeFoodSearchRepository
    private lateinit var entryRepo: FakeFoodEntryRepository
    private lateinit var customRepo: FakeCustomFoodRepository
    private lateinit var viewModel: AddFoodViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        entryRepo = FakeFoodEntryRepository()
        customRepo = FakeCustomFoodRepository()
        searchRepo = FakeFoodSearchRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- local search ---

    @Test
    fun `localSearchResults is empty when query is blank`() = runTest {
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `localSearchResults returns empty list when no match`() = runTest {
        customRepo.save(haferflocken())
        viewModel = viewModel()
        viewModel.onQueryChange("xyz")
        advanceUntilIdle()
        // emptyList() == emptyList() so StateFlow emits no new value; read current value directly
        assertTrue(viewModel.localSearchResults.value.isEmpty())
    }

    @Test
    fun `localSearchResults returns matching custom food`() = runTest {
        customRepo.save(haferflocken())
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            awaitItem() // emptyList (query blank)
            viewModel.onQueryChange("hafer")
            val results = awaitItem()
            assertEquals(1, results.size)
            val result = results[0] as LocalSearchResult.CustomFoodResult
            assertEquals("Haferflocken", result.food.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `localSearchResults returns matching history entry`() = runTest {
        entryRepo.add(entry("Banane", 150.0))
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            awaitItem() // emptyList (query blank)
            viewModel.onQueryChange("bana")
            val results = awaitItem()
            assertEquals(1, results.size)
            val result = results[0] as LocalSearchResult.HistoryResult
            assertEquals("Banane", result.entry.foodName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `localSearchResults deduplicates history entries by food name`() = runTest {
        entryRepo.add(entry("Banane", 100.0, timestampMs = 1000L))
        entryRepo.add(entry("Banane", 150.0, timestampMs = 2000L))
        entryRepo.add(entry("Banane", 200.0, timestampMs = 3000L))
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            awaitItem() // emptyList (query blank)
            viewModel.onQueryChange("Banane")
            val results = awaitItem()
            assertEquals(1, results.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `localSearchResults matching is case insensitive`() = runTest {
        customRepo.save(haferflocken())
        entryRepo.add(entry("Banane", 100.0))
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            awaitItem() // emptyList (query blank)
            viewModel.onQueryChange("HAFER")
            val haferResult = awaitItem()
            assertEquals(1, haferResult.size)
            assertTrue(haferResult[0] is LocalSearchResult.CustomFoodResult)

            viewModel.onQueryChange("BANA")
            val banaResult = awaitItem()
            assertEquals(1, banaResult.size)
            assertTrue(banaResult[0] is LocalSearchResult.HistoryResult)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `localSearchResults shows custom foods before history entries`() = runTest {
        customRepo.save(Food("", "Apfel", null, 52.0, 0.3, 14.0, 0.2))
        entryRepo.add(entry("Apfel", 120.0))
        viewModel = viewModel()
        viewModel.localSearchResults.test {
            awaitItem() // emptyList (query blank)
            viewModel.onQueryChange("apfel")
            val results = awaitItem()
            assertEquals(2, results.size)
            assertTrue(results[0] is LocalSearchResult.CustomFoodResult)
            assertTrue(results[1] is LocalSearchResult.HistoryResult)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- confirmAdd ---

    @Test
    fun `confirmAdd with valid amount saves entry and signals entryAdded`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple())
        viewModel.onAmountChange("150")
        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            val state = awaitItem()
            assertTrue(state.entryAdded)
            assertNull(state.selectedFood)
        }
    }

    @Test
    fun `confirmAdd with invalid amount does nothing`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple())
        viewModel.onAmountChange("abc")
        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            expectNoEvents()
        }
    }

    @Test
    fun `confirmAdd scales macros by amount`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple()) // 52 kcal / 100g
        viewModel.onAmountChange("200")
        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            awaitItem()
        }
        // 200g → 104 kcal
        entryRepo.entriesForDate(LocalDate.now()).test {
            val entries = awaitItem()
            assertEquals(104.0, entries.first().kcal, 0.001)
        }
    }

    // --- barcode ---

    @Test
    fun `handleBarcode selects found product`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.success(apple()))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("4001686311456")
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            val done = awaitItem()
            assertFalse(done.isLoading)
            assertEquals(apple(), done.selectedFood)
            assertEquals("4001686311456", searchRepo.lastBarcode)
        }
    }

    @Test
    fun `handleBarcode sets error when product not found`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.success(null))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("0000000000000")
            awaitItem() // loading
            val done = awaitItem()
            assertFalse(done.isLoading)
            assertEquals("Produkt nicht gefunden", done.error)
            assertNull(done.selectedFood)
        }
    }

    @Test
    fun `handleBarcode sets error on network failure`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.failure(Exception("Timeout")))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("1234567890")
            awaitItem() // loading
            val done = awaitItem()
            assertEquals("Timeout", done.error)
        }
    }

    // --- custom foods ---

    @Test
    fun `saveCustomFood saves food and selects it for amount dialog`() = runTest {
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.saveCustomFood(haferflocken())
            val state = awaitItem()
            assertNotNull(state.selectedFood)
            assertEquals("Haferflocken", state.selectedFood?.name)
            assertEquals("100", state.amountGrams)
        }
    }

    @Test
    fun `saveCustomFood assigns a real id`() = runTest {
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.saveCustomFood(Food("", "Reis", null, 130.0, 2.7, 28.0, 0.3))
            val state = awaitItem()
            assertTrue(state.selectedFood?.id?.isNotBlank() == true)
        }
    }

    // --- helpers ---

    private fun viewModel() = AddFoodViewModel(
        SavedStateHandle(mapOf("date" to LocalDate.now().toString())),
        searchRepo,
        entryRepo,
        customRepo,
    )

    private fun apple() = Food(
        id = "1",
        name = "Apfel",
        brand = null,
        kcalPer100g = 52.0,
        proteinPer100g = 0.3,
        carbsPer100g = 14.0,
        fatPer100g = 0.2,
        sugarPer100g = 10.0,
        fiberPer100g = 2.4,
    )

    private fun haferflocken() = Food(
        id = "",
        name = "Haferflocken",
        brand = null,
        kcalPer100g = 370.0,
        proteinPer100g = 13.0,
        carbsPer100g = 59.0,
        fatPer100g = 7.0,
    )

    private fun entry(
        name: String,
        amount: Double,
        timestampMs: Long = System.currentTimeMillis(),
    ) = FoodEntry(
        foodName = name,
        brand = null,
        amountGrams = amount,
        kcal = 52.0 * amount / 100.0,
        proteinG = 0.3 * amount / 100.0,
        carbsG = 14.0 * amount / 100.0,
        fatG = 0.2 * amount / 100.0,
        mealCategory = MealCategory.SNACK,
        date = LocalDate.now(),
        timestampMs = timestampMs,
    )
}
