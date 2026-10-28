package dev.antonlammers.trainist.ui.addfood

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.antonlammers.trainist.domain.model.BarcodeException
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.fake.FakeCustomFoodRepository
import dev.antonlammers.trainist.fake.FakeFoodEntryRepository
import dev.antonlammers.trainist.fake.FakeFoodSearchRepository
import dev.antonlammers.trainist.fake.FakeSavedMealRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var searchRepo: FakeFoodSearchRepository
    private lateinit var entryRepo: FakeFoodEntryRepository
    private lateinit var customRepo: FakeCustomFoodRepository
    private lateinit var mealRepo: FakeSavedMealRepository
    private lateinit var viewModel: AddFoodViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        entryRepo = FakeFoodEntryRepository()
        customRepo = FakeCustomFoodRepository()
        mealRepo = FakeSavedMealRepository()
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
    fun `confirmAdd with comma decimal saves entry`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple()) // 52 kcal / 100g
        viewModel.onAmountChange("1,5")
        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            val state = awaitItem()
            assertTrue(state.entryAdded)
        }
        entryRepo.entriesForDate(LocalDate.now()).test {
            val entries = awaitItem()
            assertEquals(1.5, entries.first().amountGrams, 0.001)
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

    // --- tags ---

    @Test
    fun `selectFood prefills the food's tag`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple().copy(tag = FoodTag.HEALTHY))
        assertEquals(FoodTag.HEALTHY, viewModel.uiState.value.tag)
    }

    @Test
    fun `confirmAdd persists the selected tag`() = runTest {
        viewModel = viewModel()
        viewModel.selectFood(apple())
        viewModel.onAmountChange("100")
        viewModel.onTagChange(FoodTag.UNHEALTHY)
        viewModel.uiState.test {
            awaitItem()
            viewModel.confirmAdd()
            awaitItem()
        }
        entryRepo.entriesForDate(LocalDate.now()).test {
            assertEquals(FoodTag.UNHEALTHY, awaitItem().first().tag)
        }
    }

    @Test
    fun `selectRecentFood carries the entry's tag`() = runTest {
        viewModel = viewModel()
        viewModel.selectRecentFood(entry("Banane", 200.0, tag = FoodTag.NEUTRAL))
        assertEquals(FoodTag.NEUTRAL, viewModel.uiState.value.tag)
        assertEquals(FoodTag.NEUTRAL, viewModel.uiState.value.selectedFood?.tag)
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
            assertEquals(BarcodeError.PRODUCT_NOT_FOUND, done.error)
            assertNull(done.selectedFood)
        }
    }

    @Test
    fun `handleBarcode shows network error for NetworkUnavailable`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.failure(BarcodeException.NetworkUnavailable))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("1234567890")
            awaitItem() // loading
            val done = awaitItem()
            assertEquals(BarcodeError.NETWORK_UNAVAILABLE, done.error)
        }
    }

    @Test
    fun `handleBarcode shows server error for ServerUnavailable`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.failure(BarcodeException.ServerUnavailable))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("1234567890")
            awaitItem() // loading
            val done = awaitItem()
            assertEquals(BarcodeError.SERVER_UNAVAILABLE, done.error)
        }
    }

    @Test
    fun `handleBarcode shows generic error for unknown exception`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.failure(RuntimeException("unexpected")))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("1234567890")
            awaitItem() // loading
            val done = awaitItem()
            assertEquals(BarcodeError.UNKNOWN, done.error)
        }
    }

    @Test
    fun `clearError resets error to null`() = runTest {
        searchRepo = FakeFoodSearchRepository(barcodeResult = Result.success(null))
        viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.handleBarcode("0000000000000")
            awaitItem() // loading
            awaitItem() // error state
            viewModel.clearError()
            assertNull(awaitItem().error)
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

    // --- updateCustomFood ---

    @Test
    fun `updateCustomFood updates food in repository`() = runTest {
        viewModel = viewModel()
        viewModel.customFoods.test {
            awaitItem() // initial empty
            val saved = customRepo.save(haferflocken())
            awaitItem() // [Haferflocken]
            viewModel.updateCustomFood(saved.copy(name = "Bio-Haferflocken"))
            val updated = awaitItem()
            assertEquals("Bio-Haferflocken", updated.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- deferred delete custom food ---

    @Test
    fun `deletePendingCustomFood hides food immediately`() = runTest {
        viewModel = viewModel()
        viewModel.customFoods.test {
            awaitItem() // initial empty
            val saved = customRepo.save(haferflocken())
            awaitItem() // [Haferflocken]
            viewModel.deletePendingCustomFood(saved)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undoDeleteCustomFood restores food`() = runTest {
        viewModel = viewModel()
        viewModel.customFoods.test {
            awaitItem() // initial empty
            val saved = customRepo.save(haferflocken())
            awaitItem() // [Haferflocken]
            viewModel.deletePendingCustomFood(saved)
            awaitItem() // hidden
            viewModel.undoDeleteCustomFood(saved)
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeleteCustomFood removes food permanently`() = runTest {
        viewModel = viewModel()
        viewModel.customFoods.test {
            awaitItem() // initial empty
            val saved = customRepo.save(haferflocken())
            awaitItem() // [Haferflocken]
            viewModel.deletePendingCustomFood(saved)
            awaitItem() // hidden
            viewModel.confirmDeleteCustomFood(saved)
            expectNoEvents()
            assertTrue(viewModel.customFoods.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- deferred delete history entry ---

    @Test
    fun `deletePendingEntry hides entry immediately`() = runTest {
        viewModel = viewModel()
        viewModel.recentEntries.test {
            awaitItem() // initial empty
            entryRepo.add(entry("Banane", 100.0))
            val withEntry = awaitItem()
            assertEquals(1, withEntry.size)
            viewModel.deletePendingEntry(withEntry.first())
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undoDeleteEntry restores entry`() = runTest {
        viewModel = viewModel()
        viewModel.recentEntries.test {
            awaitItem() // initial empty
            entryRepo.add(entry("Banane", 100.0))
            val withEntry = awaitItem()
            val e = withEntry.first()
            viewModel.deletePendingEntry(e)
            awaitItem() // hidden
            viewModel.undoDeleteEntry(e)
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeleteEntry removes entry permanently`() = runTest {
        viewModel = viewModel()
        viewModel.recentEntries.test {
            awaitItem() // initial empty
            entryRepo.add(entry("Banane", 100.0))
            val withEntry = awaitItem()
            val e = withEntry.first()
            viewModel.deletePendingEntry(e)
            awaitItem() // hidden
            viewModel.confirmDeleteEntry(e)
            expectNoEvents()
            assertTrue(viewModel.recentEntries.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- selectRecentFood (history entry -> per-100g food + prefilled amount) ---

    @Test
    fun `selectRecentFood scales macros back to per-100g and prefills the previous amount`() = runTest {
        viewModel = viewModel()
        // entry() uses a 52 kcal / 100 g base, so 200 g -> 104 kcal.
        viewModel.selectRecentFood(entry("Banane", 200.0))

        val food = viewModel.uiState.value.selectedFood
        assertNotNull(food)
        assertEquals("Banane", food!!.name)
        assertEquals(52.0, food.kcalPer100g, 0.001)
        assertEquals(14.0, food.carbsPer100g, 0.001)
        // The last used amount is pre-filled (integer formatted without decimals).
        assertEquals("200", viewModel.uiState.value.amountGrams)
    }

    @Test
    fun `selectRecentFood keeps a fractional amount as-is`() = runTest {
        viewModel = viewModel()
        viewModel.selectRecentFood(entry("Banane", 62.5))
        assertEquals("62.5", viewModel.uiState.value.amountGrams)
    }

    // --- mealCategoryForHour (time-of-day -> default meal) ---

    @Test
    fun `mealCategoryForHour maps each time band`() {
        assertEquals(MealCategory.BREAKFAST, mealCategoryForHour(5))
        assertEquals(MealCategory.BREAKFAST, mealCategoryForHour(9))
        assertEquals(MealCategory.LUNCH, mealCategoryForHour(10))
        assertEquals(MealCategory.LUNCH, mealCategoryForHour(13))
        assertEquals(MealCategory.DINNER, mealCategoryForHour(17))
        assertEquals(MealCategory.DINNER, mealCategoryForHour(21))
    }

    @Test
    fun `mealCategoryForHour falls back to snack outside the meal bands`() {
        assertEquals(MealCategory.SNACK, mealCategoryForHour(4))
        assertEquals(MealCategory.SNACK, mealCategoryForHour(14))
        assertEquals(MealCategory.SNACK, mealCategoryForHour(16))
        assertEquals(MealCategory.SNACK, mealCategoryForHour(22))
        assertEquals(MealCategory.SNACK, mealCategoryForHour(0))
    }

    // --- helpers ---

    // --- online search (Open Food Facts) ---

    @Test
    fun `a query shorter than three characters never reaches the network`() = runTest {
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }

        viewModel.onQueryChange("mi")
        advanceUntilIdle()

        assertTrue(searchRepo.searchedQueries.isEmpty())
        assertEquals(RemoteSearchState(), viewModel.remoteSearchResults.value)
    }

    @Test
    fun `typing is debounced into a single request for the final query`() = runTest {
        searchRepo.respondWith(Result.success(listOf(apple())))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }

        // Keystrokes arriving faster than the debounce window must not each become a request — the
        // search service is a shared, rate-limited resource.
        viewModel.onQueryChange("app")
        advanceTimeBy(100)
        viewModel.onQueryChange("apfe")
        advanceTimeBy(100)
        viewModel.onQueryChange("apfel")
        advanceUntilIdle()

        assertEquals(listOf("apfel"), searchRepo.searchedQueries)
    }

    @Test
    fun `results are exposed once the search returns`() = runTest {
        searchRepo.respondWith(Result.success(listOf(apple())))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }

        viewModel.onQueryChange("apfel")
        advanceUntilIdle()

        val state = viewModel.remoteSearchResults.value
        assertEquals(listOf("Apfel"), state.results.map { it.name })
        assertFalse(state.isLoading)
        assertFalse(state.failed)
    }

    @Test
    fun `the section reports loading before the results arrive`() = runTest {
        searchRepo.respondWith(Result.success(listOf(apple())))
        viewModel = viewModel()

        viewModel.remoteSearchResults.test {
            assertEquals(RemoteSearchState(), awaitItem())
            viewModel.onQueryChange("apfel")
            assertTrue(awaitItem().isLoading)
            assertEquals(listOf("Apfel"), awaitItem().results.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed search is reported as failed, not as an empty result`() = runTest {
        // The UI shows different text for the two, and only one of them is worth retrying.
        searchRepo.respondWith(Result.failure(BarcodeException.NetworkUnavailable))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }

        viewModel.onQueryChange("apfel")
        advanceUntilIdle()

        val state = viewModel.remoteSearchResults.value
        assertTrue(state.failed)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `clearing the query drops the previous results`() = runTest {
        searchRepo.respondWith(Result.success(listOf(apple())))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }
        viewModel.onQueryChange("apfel")
        advanceUntilIdle()

        viewModel.onQueryChange("")
        advanceUntilIdle()

        assertEquals(RemoteSearchState(), viewModel.remoteSearchResults.value)
    }

    @Test
    fun `whitespace-only changes to a query do not re-run the search`() = runTest {
        searchRepo.respondWith(Result.success(listOf(apple())))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.remoteSearchResults.collect {} }

        viewModel.onQueryChange("apfel")
        advanceUntilIdle()
        viewModel.onQueryChange("apfel ")
        advanceUntilIdle()

        assertEquals(listOf("apfel"), searchRepo.searchedQueries)
    }

    // --- saved meals ---

    @Test
    fun `localSearchResults lists matching saved meals before custom foods and history`() = runTest {
        mealRepo.save(muesli())
        customRepo.save(haferflocken().copy(name = "Müsliriegel"))
        viewModel = viewModel()
        backgroundScope.launch { viewModel.localSearchResults.collect {} }

        viewModel.onQueryChange("müsli")
        advanceUntilIdle()

        val results = viewModel.localSearchResults.value
        assertEquals(2, results.size)
        assertEquals("Müsli", (results[0] as LocalSearchResult.SavedMealResult).meal.name)
        assertTrue(results[1] is LocalSearchResult.CustomFoodResult)
    }

    @Test
    fun `selectMeal opens the log sheet with every saved amount`() = runTest {
        viewModel = viewModel()
        viewModel.onMealCategoryChange(MealCategory.DINNER)

        viewModel.selectMeal(muesli())

        val draft = viewModel.uiState.value.mealToLog!!
        assertEquals(listOf("60", "150", "200.5"), draft.amounts)
        // The category follows the time of day again, like for a single food.
        assertEquals(mealCategoryForHour(java.time.LocalTime.now().hour), viewModel.uiState.value.mealCategory)
    }

    @Test
    fun `confirmLogMeal adds one entry per ingredient with the edited amounts`() = runTest {
        val date = LocalDate.parse("2026-10-27")
        mealRepo.save(muesli())
        viewModel = viewModel(date)
        viewModel.selectMeal(mealRepo.meals().first().single())
        viewModel.onMealLogAmountChange(0, "80")
        viewModel.onMealCategoryChange(MealCategory.SNACK)

        viewModel.confirmLogMeal()
        advanceUntilIdle()

        val logged = entryRepo.allEntries()
        assertEquals(listOf("Haferflocken", "Magerquark", "Milch"), logged.map { it.foodName })
        assertEquals(listOf(80.0, 150.0, 200.5), logged.map { it.amountGrams })
        assertEquals(296.0, logged[0].kcal, 0.001)
        assertEquals(listOf(FoodTag.HEALTHY, FoodTag.NEUTRAL, FoodTag.NONE), logged.map { it.tag })
        assertTrue(logged.all { it.date == date && it.mealCategory == MealCategory.SNACK })
        assertTrue(viewModel.uiState.value.entryAdded)
        assertNull(viewModel.uiState.value.mealToLog)
        // Logging never changes the meal itself.
        assertEquals(60.0, mealRepo.meals().first().single().items.first().amountGrams, 0.0)
    }

    @Test
    fun `a cleared or zero amount leaves that ingredient out of this entry`() = runTest {
        viewModel = viewModel()
        viewModel.selectMeal(muesli())
        viewModel.onMealLogAmountChange(1, "")
        viewModel.onMealLogAmountChange(2, "0")

        viewModel.confirmLogMeal()
        advanceUntilIdle()

        assertEquals(listOf("Haferflocken"), entryRepo.allEntries().map { it.foodName })
    }

    @Test
    fun `confirmLogMeal does nothing when every ingredient was cleared`() = runTest {
        viewModel = viewModel()
        viewModel.selectMeal(muesli())
        repeat(3) { viewModel.onMealLogAmountChange(it, "") }

        viewModel.confirmLogMeal()
        advanceUntilIdle()

        assertTrue(entryRepo.allEntries().isEmpty())
        assertFalse(viewModel.uiState.value.entryAdded)
        assertNotNull(viewModel.uiState.value.mealToLog)
    }

    @Test
    fun `dismissMealLog closes the sheet without logging`() = runTest {
        viewModel = viewModel()
        viewModel.selectMeal(muesli())

        viewModel.dismissMealLog()
        viewModel.confirmLogMeal()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.mealToLog)
        assertTrue(entryRepo.allEntries().isEmpty())
    }

    @Test
    fun `deleting a meal hides it at once, undo restores it, confirm removes it`() = runTest {
        mealRepo.save(muesli())
        viewModel = viewModel()
        backgroundScope.launch { viewModel.savedMeals.collect {} }
        advanceUntilIdle()
        val meal = viewModel.savedMeals.value.single()

        viewModel.deletePendingMeal(meal)
        advanceUntilIdle()
        assertTrue(viewModel.savedMeals.value.isEmpty())

        viewModel.undoDeleteMeal(meal)
        advanceUntilIdle()
        assertEquals(1, viewModel.savedMeals.value.size)

        viewModel.deletePendingMeal(meal)
        viewModel.confirmDeleteMeal(meal)
        advanceUntilIdle()
        assertTrue(viewModel.savedMeals.value.isEmpty())
        assertTrue(mealRepo.meals().first().isEmpty())
    }

    // --- picking an ingredient ---

    @Test
    fun `takeIngredient hands the selection over with its amount and tag instead of logging it`() = runTest {
        viewModel = viewModel()
        viewModel.onQueryChange("hafer")
        viewModel.selectFood(haferflocken())
        viewModel.onAmountChange("45,5")
        viewModel.onTagChange(FoodTag.HEALTHY)

        val item = viewModel.takeIngredient()!!
        advanceUntilIdle()

        assertEquals("Haferflocken", item.food.name)
        assertEquals(45.5, item.amountGrams, 0.0)
        assertEquals(FoodTag.HEALTHY, item.food.tag)
        assertNull(viewModel.uiState.value.selectedFood)
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(entryRepo.allEntries().isEmpty())
        assertFalse(viewModel.uiState.value.entryAdded)
    }

    @Test
    fun `takeIngredient returns nothing without a valid amount`() = runTest {
        viewModel = viewModel()
        assertNull(viewModel.takeIngredient())

        viewModel.selectFood(haferflocken())
        viewModel.onAmountChange("0")

        assertNull(viewModel.takeIngredient())
        assertNotNull(viewModel.uiState.value.selectedFood)
    }

    private fun muesli() = SavedMeal(
        stableId = "meal-muesli",
        name = "Müsli",
        items = listOf(
            SavedMealItem(haferflocken().copy(tag = FoodTag.HEALTHY), 60.0),
            SavedMealItem(haferflocken().copy(name = "Magerquark", kcalPer100g = 68.0, tag = FoodTag.NEUTRAL), 150.0),
            SavedMealItem(haferflocken().copy(name = "Milch", kcalPer100g = 47.0), 200.5),
        ),
    )

    private fun viewModel(date: LocalDate = LocalDate.now()) = AddFoodViewModel(
        SavedStateHandle(mapOf("date" to date.toString())),
        searchRepo,
        entryRepo,
        customRepo,
        mealRepo,
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
        tag: FoodTag = FoodTag.NONE,
    ) = FoodEntry(
        foodName = name,
        brand = null,
        amountGrams = amount,
        kcal = 52.0 * amount / 100.0,
        proteinG = 0.3 * amount / 100.0,
        carbsG = 14.0 * amount / 100.0,
        fatG = 0.2 * amount / 100.0,
        mealCategory = MealCategory.SNACK,
        tag = tag,
        date = LocalDate.now(),
        timestampMs = timestampMs,
    )
}
