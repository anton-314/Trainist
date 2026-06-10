package dev.antonlammers.macrotrac.ui.overview

import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.fake.FakeFoodEntryRepository
import dev.antonlammers.macrotrac.fake.FakeGoalRepository
import dev.antonlammers.macrotrac.fake.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var foodEntryRepo: FakeFoodEntryRepository
    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var weightRepo: FakeWeightRepository
    private lateinit var viewModel: OverviewViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        foodEntryRepo = FakeFoodEntryRepository()
        goalRepo = FakeGoalRepository()
        weightRepo = FakeWeightRepository()
        viewModel = OverviewViewModel(foodEntryRepo, goalRepo, weightRepo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state has empty entries and default goal`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.entries.isEmpty())
            assertEquals(DailyGoal(), state.goal)
        }
    }

    @Test
    fun `entries added today appear in state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            foodEntryRepo.add(buildEntry(kcal = 300.0, date = LocalDate.now()))
            val state = awaitItem()

            assertEquals(1, state.entries.size)
            assertEquals(300.0, state.totalKcal, 0.001)
        }
    }

    @Test
    fun `entries from other dates are not shown`() = runTest {
        foodEntryRepo.add(buildEntry(kcal = 500.0, date = LocalDate.now().minusDays(1)))

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.entries.isEmpty())
        }
    }

    @Test
    fun `deletePending hides entry immediately from state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            foodEntryRepo.add(buildEntry(kcal = 200.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            val entry = afterAdd.entries.first()

            viewModel.deletePending(entry)
            val afterPending = awaitItem()
            assertTrue(afterPending.entries.isEmpty())
        }
    }

    @Test
    fun `confirmDelete removes entry from DB permanently`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            foodEntryRepo.add(buildEntry(kcal = 200.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            val entry = afterAdd.entries.first()

            viewModel.deletePending(entry)
            awaitItem() // hidden

            viewModel.confirmDelete(entry)
            // state stays empty — pending cleared, DB deleted
            expectNoEvents()
            assertTrue(viewModel.uiState.value.entries.isEmpty())
        }
    }

    @Test
    fun `undoDelete restores entry in state`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            foodEntryRepo.add(buildEntry(kcal = 200.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            val entry = afterAdd.entries.first()

            viewModel.deletePending(entry)
            awaitItem() // hidden

            viewModel.undoDelete(entry)
            val restored = awaitItem()
            assertEquals(1, restored.entries.size)
            assertEquals(200.0, restored.totalKcal, 0.001)
        }
    }

    @Test
    fun `totalKcal sums all entries correctly`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            foodEntryRepo.add(buildEntry(kcal = 100.0, date = LocalDate.now()))
            awaitItem()
            foodEntryRepo.add(buildEntry(kcal = 250.0, date = LocalDate.now()))
            val state = awaitItem()

            assertEquals(350.0, state.totalKcal, 0.001)
        }
    }

    @Test
    fun `goal update reflects in state`() = runTest {
        val newGoal = DailyGoal(kcal = 1800.0, proteinG = 120.0, carbsG = 200.0, fatG = 60.0)

        viewModel.uiState.test {
            awaitItem()
            goalRepo.save(newGoal)
            val state = awaitItem()
            assertEquals(1800.0, state.goal.kcal, 0.001)
        }
    }

    @Test
    fun `previousDay shows entries from yesterday`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        foodEntryRepo.add(buildEntry(kcal = 400.0, date = yesterday))

        viewModel.uiState.test {
            awaitItem() // today, empty

            viewModel.previousDay()
            val state = awaitItem()

            assertEquals(yesterday, state.date)
            assertEquals(1, state.entries.size)
            assertEquals(400.0, state.totalKcal, 0.001)
        }
    }

    @Test
    fun `nextDay navigates to tomorrow and shows no entries`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1)

        viewModel.uiState.test {
            awaitItem() // today

            viewModel.nextDay()
            val state = awaitItem()

            assertEquals(tomorrow, state.date)
            assertTrue(state.entries.isEmpty())
        }
    }

    @Test
    fun `updating an entry recalculates totals`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            foodEntryRepo.add(buildEntry(kcal = 200.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            val original = afterAdd.entries.first()

            viewModel.update(original.copy(kcal = 350.0, amountGrams = 175.0))
            val afterEdit = awaitItem()

            assertEquals(350.0, afterEdit.totalKcal, 0.001)
            assertEquals(175.0, afterEdit.entries.first().amountGrams, 0.001)
        }
    }

    @Test
    fun `updating meal category changes category without affecting kcal`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            foodEntryRepo.add(buildEntry(kcal = 300.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            val original = afterAdd.entries.first()

            viewModel.update(original.copy(mealCategory = dev.antonlammers.macrotrac.domain.model.MealCategory.DINNER))
            val afterEdit = awaitItem()

            assertEquals(dev.antonlammers.macrotrac.domain.model.MealCategory.DINNER, afterEdit.entries.first().mealCategory)
            assertEquals(300.0, afterEdit.totalKcal, 0.001)
        }
    }

    @Test
    fun `goToToday returns to today from a different date`() = runTest {
        viewModel.uiState.test {
            awaitItem() // today

            viewModel.previousDay()
            awaitItem() // yesterday

            viewModel.goToToday()
            val state = awaitItem()

            assertEquals(LocalDate.now(), state.date)
        }
    }

    @Test
    fun `kcalForMeal sums only that meal's entries`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            foodEntryRepo.add(buildEntry(kcal = 300.0, date = LocalDate.now(), mealCategory = MealCategory.BREAKFAST))
            awaitItem()
            foodEntryRepo.add(buildEntry(kcal = 500.0, date = LocalDate.now(), mealCategory = MealCategory.LUNCH))
            val state = awaitItem()

            assertEquals(300.0, state.kcalForMeal(MealCategory.BREAKFAST), 0.001)
            assertEquals(500.0, state.kcalForMeal(MealCategory.LUNCH), 0.001)
            assertEquals(0.0, state.kcalForMeal(MealCategory.DINNER), 0.001)
        }
    }

    @Test
    fun `copyableMeals lists main meals empty today but present yesterday`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        foodEntryRepo.add(buildEntry(kcal = 300.0, date = yesterday, mealCategory = MealCategory.BREAKFAST))
        foodEntryRepo.add(buildEntry(kcal = 400.0, date = yesterday, mealCategory = MealCategory.DINNER))
        // A snack yesterday must never be offered for copy.
        foodEntryRepo.add(buildEntry(kcal = 100.0, date = yesterday, mealCategory = MealCategory.SNACK))

        viewModel.uiState.test {
            awaitItem() // initial default
            val state = awaitItem() // computed: today empty, yesterday populated

            assertEquals(setOf(MealCategory.BREAKFAST, MealCategory.DINNER), state.copyableMeals)
        }
    }

    @Test
    fun `meal already filled today is not copyable`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        foodEntryRepo.add(buildEntry(kcal = 300.0, date = yesterday, mealCategory = MealCategory.BREAKFAST))
        foodEntryRepo.add(buildEntry(kcal = 250.0, date = LocalDate.now(), mealCategory = MealCategory.BREAKFAST))

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()

            assertTrue(MealCategory.BREAKFAST !in state.copyableMeals)
        }
    }

    @Test
    fun `copyMealFromPreviousDay copies yesterday's meal into today with same amounts`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        foodEntryRepo.add(buildEntry(kcal = 300.0, date = yesterday, mealCategory = MealCategory.BREAKFAST, amountGrams = 80.0))
        foodEntryRepo.add(buildEntry(kcal = 500.0, date = yesterday, mealCategory = MealCategory.LUNCH))

        viewModel.uiState.test {
            awaitItem() // initial
            val before = awaitItem()
            assertTrue(before.entries.isEmpty())
            assertEquals(setOf(MealCategory.BREAKFAST, MealCategory.LUNCH), before.copyableMeals)

            viewModel.copyMealFromPreviousDay(MealCategory.BREAKFAST)
            val after = awaitItem()

            val breakfast = after.entriesForMeal(MealCategory.BREAKFAST)
            assertEquals(1, breakfast.size)
            assertEquals(LocalDate.now(), breakfast.first().date)
            assertEquals(80.0, breakfast.first().amountGrams, 0.001)
            assertEquals(300.0, after.kcalForMeal(MealCategory.BREAKFAST), 0.001)
            // Breakfast is now filled, so only lunch remains copyable.
            assertEquals(setOf(MealCategory.LUNCH), after.copyableMeals)
        }
    }

    private fun buildEntry(
        kcal: Double,
        date: LocalDate,
        mealCategory: MealCategory = MealCategory.SNACK,
        amountGrams: Double = 100.0,
    ) = FoodEntry(
        foodName = "Testessen",
        brand = null,
        amountGrams = amountGrams,
        kcal = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        sugarG = 3.0,
        fiberG = 1.5,
        mealCategory = mealCategory,
        date = date,
        timestampMs = System.currentTimeMillis(),
    )
}
