package dev.antonlammers.macromind.ui.overview

import app.cash.turbine.test
import dev.antonlammers.macromind.domain.model.DailyGoal
import dev.antonlammers.macromind.domain.model.FoodEntry
import dev.antonlammers.macromind.fake.FakeFoodEntryRepository
import dev.antonlammers.macromind.fake.FakeGoalRepository
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
    private lateinit var viewModel: OverviewViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        foodEntryRepo = FakeFoodEntryRepository()
        goalRepo = FakeGoalRepository()
        viewModel = OverviewViewModel(foodEntryRepo, goalRepo)
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
    fun `deleting an entry removes it from state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            foodEntryRepo.add(buildEntry(kcal = 200.0, date = LocalDate.now()))
            val afterAdd = awaitItem()
            assertEquals(1, afterAdd.entries.size)

            viewModel.delete(afterAdd.entries.first().id)
            val afterDelete = awaitItem()
            assertTrue(afterDelete.entries.isEmpty())
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

    private fun buildEntry(kcal: Double, date: LocalDate) = FoodEntry(
        foodName = "Testessen",
        brand = null,
        amountGrams = 100.0,
        kcal = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        date = date,
        timestampMs = System.currentTimeMillis(),
    )
}
