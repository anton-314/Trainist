package dev.antonlammers.macrotrac.ui.addfood

import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.fake.FakeCustomFoodRepository
import dev.antonlammers.macrotrac.fake.FakeFoodEntryRepository
import dev.antonlammers.macrotrac.fake.FakeFoodSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search updates results on success`() = runTest {
        searchRepo = FakeFoodSearchRepository(searchResult = Result.success(listOf(apple())))
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.onQueryChange("apfel")

        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.search()
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val done = awaitItem()
            assertFalse(done.isLoading)
            assertEquals(1, done.results.size)
            assertEquals("Apfel", done.results.first().name)
        }
    }

    @Test
    fun `search sets error on failure`() = runTest {
        searchRepo = FakeFoodSearchRepository(searchResult = Result.failure(Exception("Netzwerkfehler")))
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.onQueryChange("xyz")

        viewModel.uiState.test {
            awaitItem()

            viewModel.search()
            awaitItem() // loading
            val done = awaitItem()

            assertFalse(done.isLoading)
            assertEquals("Netzwerkfehler", done.error)
            assertTrue(done.results.isEmpty())
        }
    }

    @Test
    fun `blank query does not trigger search`() = runTest {
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.uiState.test {
            awaitItem()
            viewModel.search()
            expectNoEvents()
            assertNull(searchRepo.lastQuery)
        }
    }

    @Test
    fun `confirmAdd with valid amount saves entry and signals entryAdded`() = runTest {
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

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
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

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
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.selectFood(apple()) // 52 kcal / 100g
        viewModel.onAmountChange("200")

        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            awaitItem()
        }

        // 200g → 104 kcal
        val saved = entryRepo.entriesForDate(java.time.LocalDate.now())
        saved.test {
            val entries = awaitItem()
            assertEquals(104.0, entries.first().kcal, 0.001)
        }
    }

    @Test
    fun `handleBarcode selects found product`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.success(apple()))
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

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
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

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
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("1234567890")
            awaitItem() // loading
            val done = awaitItem()
            assertEquals("Timeout", done.error)
        }
    }

    @Test
    fun `saveCustomFood saves food and selects it for amount dialog`() = runTest {
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        val food = Food(
            id = "",
            name = "Haferflocken",
            brand = null,
            kcalPer100g = 370.0,
            proteinPer100g = 13.0,
            carbsPer100g = 59.0,
            fatPer100g = 7.0,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.saveCustomFood(food)
            val state = awaitItem()
            assertNotNull(state.selectedFood)
            assertEquals("Haferflocken", state.selectedFood?.name)
            assertEquals("100", state.amountGrams)
        }
    }

    @Test
    fun `saveCustomFood assigns a real id`() = runTest {
        searchRepo = FakeFoodSearchRepository()
        viewModel = AddFoodViewModel(searchRepo, entryRepo, customRepo)

        viewModel.uiState.test {
            awaitItem()
            viewModel.saveCustomFood(Food("", "Reis", null, 130.0, 2.7, 28.0, 0.3))
            val state = awaitItem()
            assertTrue(state.selectedFood?.id?.isNotBlank() == true)
        }
    }

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
}
