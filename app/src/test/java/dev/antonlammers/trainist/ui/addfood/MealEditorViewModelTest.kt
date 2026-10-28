package dev.antonlammers.trainist.ui.addfood

import androidx.lifecycle.SavedStateHandle
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import dev.antonlammers.trainist.fake.FakeFoodEntryRepository
import dev.antonlammers.trainist.fake.FakeSavedMealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MealEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mealRepo: FakeSavedMealRepository
    private lateinit var entryRepo: FakeFoodEntryRepository
    private val date = LocalDate.parse("2026-10-28")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = FakeSavedMealRepository()
        entryRepo = FakeFoodEntryRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- starting ---

    @Test
    fun `no draft until the meal sheet is opened`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.draft)
    }

    @Test
    fun `startNew opens an empty draft and keeps an existing one`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.onNameChange("Müsli")

        vm.startNew()

        assertEquals(MealDraftOrigin.NEW, vm.uiState.value.draft?.origin)
        assertEquals("Müsli", vm.uiState.value.draft?.name)
    }

    @Test
    fun `edit loads the meal's name and amounts`() = runTest {
        val vm = viewModel()

        vm.edit(muesli(id = 7))

        val draft = vm.uiState.value.draft!!
        assertEquals(MealDraftOrigin.EDIT, draft.origin)
        assertEquals("Müsli", draft.name)
        assertEquals(listOf("60", "150", "200.5"), draft.items.map { it.amount })
    }

    @Test
    fun `the saveMeal route argument starts a draft from that day's entries of the category`() = runTest {
        entryRepo.add(entry("Milch", 200.0, MealCategory.BREAKFAST, timestamp = 3))
        entryRepo.add(entry("Haferflocken", 60.0, MealCategory.BREAKFAST, timestamp = 1))
        entryRepo.add(entry("Apfel", 150.0, MealCategory.SNACK, timestamp = 2))
        entryRepo.add(entry("Toast", 50.0, MealCategory.BREAKFAST, timestamp = 4, date = date.minusDays(1)))

        val vm = viewModel(saveMeal = MealCategory.BREAKFAST)
        advanceUntilIdle()

        val draft = vm.uiState.value.draft!!
        assertEquals(MealDraftOrigin.FROM_DAY, draft.origin)
        // In the order they were eaten, the other category and the other day left out.
        assertEquals(listOf("Haferflocken", "Milch"), draft.items.map { it.food.name })
        assertEquals(listOf("60", "200"), draft.items.map { it.amount })
        // Per-100 g values recovered from the logged totals.
        assertEquals(100.0, draft.items.first().food.kcalPer100g, 0.001)
        assertEquals("", draft.name)
    }

    @Test
    fun `an unknown saveMeal argument opens nothing`() = runTest {
        val vm = MealEditorViewModel(
            SavedStateHandle(mapOf("date" to date.toString(), MealEditorViewModel.SAVE_MEAL_ARG to "BRUNCH")),
            mealRepo,
            entryRepo,
        )
        advanceUntilIdle()

        assertNull(vm.uiState.value.draft)
    }

    // --- editing ---

    @Test
    fun `amounts and removals address one row, also when the same food is in twice`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.addItem(SavedMealItem(food("Haferflocken"), 60.0))
        vm.addItem(SavedMealItem(food("Haferflocken"), 30.0))
        val (first, second) = vm.uiState.value.draft!!.items
        assertNotEquals(first.key, second.key)

        vm.onAmountChange(second.key, "40")
        vm.removeItem(first.key)

        assertEquals(listOf("40"), vm.uiState.value.draft!!.items.map { it.amount })
    }

    @Test
    fun `picking hides the sheet and a picked ingredient brings it back with the ingredient`() = runTest {
        val vm = viewModel()
        vm.startNew()

        vm.startPicking()
        assertTrue(vm.uiState.value.picking)
        vm.addItem(SavedMealItem(food("Milch", tag = FoodTag.NEUTRAL), 200.0))

        assertFalse(vm.uiState.value.picking)
        val item = vm.uiState.value.draft!!.items.single()
        assertEquals("Milch", item.food.name)
        assertEquals(FoodTag.NEUTRAL, item.food.tag)
        assertEquals("200", item.amount)
    }

    @Test
    fun `cancelPicking returns to the draft unchanged`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.onNameChange("Müsli")
        vm.startPicking()

        vm.cancelPicking()

        assertFalse(vm.uiState.value.picking)
        assertEquals("Müsli", vm.uiState.value.draft?.name)
    }

    @Test
    fun `picking needs a draft to return to`() = runTest {
        val vm = viewModel()

        vm.startPicking()

        assertFalse(vm.uiState.value.picking)
    }

    @Test
    fun `discard drops the draft`() = runTest {
        val vm = viewModel()
        vm.startNew()

        vm.discard()

        assertEquals(MealEditorUiState(), vm.uiState.value)
    }

    // --- validation + totals ---

    @Test
    fun `a meal needs a name, an ingredient and a positive amount for every ingredient`() = runTest {
        val vm = viewModel()
        vm.startNew()
        assertFalse(vm.uiState.value.draft!!.canSave)

        vm.onNameChange("  ")
        vm.addItem(SavedMealItem(food("Milch"), 200.0))
        assertFalse(vm.uiState.value.draft!!.canSave)

        vm.onNameChange("Müsli")
        assertTrue(vm.uiState.value.draft!!.canSave)

        val key = vm.uiState.value.draft!!.items.single().key
        vm.onAmountChange(key, "0")
        assertFalse(vm.uiState.value.draft!!.canSave)
        vm.onAmountChange(key, "")
        assertFalse(vm.uiState.value.draft!!.canSave)
        vm.onAmountChange(key, "150,5")
        assertTrue(vm.uiState.value.draft!!.canSave)
    }

    @Test
    fun `totals follow the typed amounts`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.addItem(SavedMealItem(food("Haferflocken"), 50.0))
        vm.addItem(SavedMealItem(food("Milch"), 200.0))

        assertEquals(250.0, vm.uiState.value.draft!!.totals.kcal, 0.001)

        vm.onAmountChange(vm.uiState.value.draft!!.items.last().key, "abc")
        assertEquals(50.0, vm.uiState.value.draft!!.totals.kcal, 0.001)
    }

    // --- saving ---

    @Test
    fun `saving a new meal stores it with a fresh stable key and reports it once`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.onNameChange("  Müsli ")
        vm.addItem(SavedMealItem(food("Haferflocken"), 60.0))

        vm.save()
        advanceUntilIdle()

        val stored = mealRepo.meals().first().single()
        assertEquals("Müsli", stored.name)
        assertTrue(stored.stableId.isNotBlank())
        assertEquals(listOf(60.0), stored.items.map { it.amountGrams })
        val saved = vm.uiState.value.saved!!
        assertEquals(stored, saved.meal)
        assertEquals(MealDraftOrigin.NEW, saved.origin)
        assertNull(vm.uiState.value.draft)

        vm.savedHandled()
        assertNull(vm.uiState.value.saved)
    }

    @Test
    fun `saving an edited meal overwrites it and keeps its stable key`() = runTest {
        val id = mealRepo.save(muesli())
        val vm = viewModel()
        vm.edit(mealRepo.meals().first().single())
        vm.onNameChange("Müsli groß")
        vm.removeItem(vm.uiState.value.draft!!.items.last().key)

        vm.save()
        advanceUntilIdle()

        val stored = mealRepo.meals().first().single()
        assertEquals(id, stored.id)
        assertEquals("meal-muesli", stored.stableId)
        assertEquals("Müsli groß", stored.name)
        assertEquals(2, stored.items.size)
        assertEquals(MealDraftOrigin.EDIT, vm.uiState.value.saved?.origin)
    }

    @Test
    fun `editing a meal keeps when it was last used`() = runTest {
        val id = mealRepo.save(muesli())
        mealRepo.markUsed(id, atMs = 42)
        val vm = viewModel()
        vm.edit(mealRepo.meals().first().single())
        vm.onNameChange("Müsli groß")

        vm.save()
        advanceUntilIdle()

        assertEquals(42L, mealRepo.meals().first().single().lastUsedMs)
    }

    @Test
    fun `a double tap on save stores a new meal once`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.onNameChange("Müsli")
        vm.addItem(SavedMealItem(food("Haferflocken"), 60.0))

        // A new meal's stable key is minted per save, so the upsert alone would not catch this.
        vm.save()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, mealRepo.meals().first().size)
    }

    @Test
    fun `an invalid draft is not saved`() = runTest {
        val vm = viewModel()
        vm.startNew()
        vm.addItem(SavedMealItem(food("Milch"), 200.0))

        vm.save()
        advanceUntilIdle()

        assertTrue(mealRepo.meals().first().isEmpty())
        assertNull(vm.uiState.value.saved)
    }

    @Test
    fun `a meal saved from the day reports that origin`() = runTest {
        entryRepo.add(entry("Haferflocken", 60.0, MealCategory.BREAKFAST, timestamp = 1))
        val vm = viewModel(saveMeal = MealCategory.BREAKFAST)
        advanceUntilIdle()
        vm.onNameChange("Frühstück")

        vm.save()
        advanceUntilIdle()

        assertEquals(MealDraftOrigin.FROM_DAY, vm.uiState.value.saved?.origin)
        assertEquals("Haferflocken", mealRepo.meals().first().single().items.single().food.name)
    }

    private fun viewModel(saveMeal: MealCategory? = null) = MealEditorViewModel(
        SavedStateHandle(
            buildMap {
                put("date", date.toString())
                saveMeal?.let { put(MealEditorViewModel.SAVE_MEAL_ARG, it.name) }
            },
        ),
        mealRepo,
        entryRepo,
    )

    private fun food(name: String, tag: FoodTag = FoodTag.NONE) = Food(
        id = "", name = name, brand = null,
        kcalPer100g = 100.0, proteinPer100g = 10.0, carbsPer100g = 5.0, fatPer100g = 1.0, tag = tag,
    )

    private fun muesli(id: Long = 0) = SavedMeal(
        id = id,
        stableId = "meal-muesli",
        name = "Müsli",
        items = listOf(
            SavedMealItem(food("Haferflocken"), 60.0),
            SavedMealItem(food("Magerquark"), 150.0),
            SavedMealItem(food("Milch"), 200.5),
        ),
    )

    private fun entry(
        name: String,
        amount: Double,
        category: MealCategory,
        timestamp: Long,
        date: LocalDate = this.date,
    ) = FoodEntry(
        foodName = name, brand = null, amountGrams = amount,
        kcal = amount, proteinG = amount / 10, carbsG = 0.0, fatG = 0.0,
        mealCategory = category, date = date, timestampMs = timestamp,
    )
}
