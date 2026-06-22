package dev.antonlammers.macrotrac.ui.stats

import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.model.WeightEntry
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
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var foodRepo: FakeFoodEntryRepository
    private lateinit var weightRepo: FakeWeightRepository
    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        foodRepo = FakeFoodEntryRepository()
        weightRepo = FakeWeightRepository()
        goalRepo = FakeGoalRepository()
        viewModel = StatsViewModel(foodRepo, weightRepo, goalRepo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state has WEEK time range`() = runTest {
        viewModel.uiState.test {
            assertEquals(TimeRange.WEEK, awaitItem().timeRange)
        }
    }

    @Test
    fun `switching to MONTH changes range and emits 30 calorie points`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial WEEK

            viewModel.setTimeRange(TimeRange.MONTH)
            val state = awaitItem()

            assertEquals(TimeRange.MONTH, state.timeRange)
            assertEquals(30, state.caloriePoints.size)
        }
    }

    @Test
    fun `switching to YEAR emits 12 monthly calorie points`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setTimeRange(TimeRange.YEAR)
            val state = awaitItem()

            assertEquals(TimeRange.YEAR, state.timeRange)
            assertEquals(12, state.caloriePoints.size)
        }
    }

    @Test
    fun `today's food entry appears in calorie points for WEEK`() = runTest {
        val today = LocalDate.now()
        foodRepo.add(buildEntry(kcal = 500.0, date = today))

        viewModel.uiState.test {
            awaitItem() // initial empty state
            val state = awaitItem() // populated state

            val todayPoint = state.caloriePoints.last()
            assertEquals(500.0, todayPoint.value, 0.001)
        }
    }

    @Test
    fun `entries outside range are not included`() = runTest {
        foodRepo.add(buildEntry(kcal = 999.0, date = LocalDate.now().minusDays(10)))

        viewModel.uiState.test {
            awaitItem() // initial empty
            val state = awaitItem() // populated — 7 points, all 0
            assertEquals(7, state.caloriePoints.size)
            assertTrue(state.caloriePoints.all { it.value == 0.0 })
        }
    }

    @Test
    fun `weight entries appear as date-sorted samples with current and delta`() = runTest {
        val today = LocalDate.now()
        weightRepo.save(WeightEntry(weightKg = 80.0, date = today.minusDays(1), timestampMs = 1))
        weightRepo.save(WeightEntry(weightKg = 79.5, date = today, timestampMs = 2))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.weight.samples.size < 2) state = awaitItem()

            assertEquals(listOf(80.0, 79.5), state.weight.samples.map { it.kg })
            assertEquals(79.5, state.weight.current!!, 0.001)
            assertEquals(-0.5, state.weight.delta!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `target weight from goal flows into weight chart data`() = runTest {
        goalRepo.save(DailyGoal(targetWeightKg = 70.0))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.weight.targetKg != 70.0) state = awaitItem()
            assertEquals(70.0, state.weight.targetKg!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `YEAR view aggregates weigh-ins per calendar month`() = runTest {
        val today = LocalDate.now()
        // Two weigh-ins in the previous month (→ averaged) plus one in the current month.
        val prevMonthStart = today.minusMonths(1).withDayOfMonth(1)
        weightRepo.save(WeightEntry(weightKg = 80.0, date = prevMonthStart, timestampMs = 1))
        weightRepo.save(WeightEntry(weightKg = 82.0, date = prevMonthStart.plusDays(1), timestampMs = 2))
        weightRepo.save(WeightEntry(weightKg = 79.0, date = today, timestampMs = 3))
        viewModel.setTimeRange(TimeRange.YEAR)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!(state.timeRange == TimeRange.YEAR && state.weight.samples.size >= 2)) state = awaitItem()

            assertEquals(2, state.weight.samples.size)
            assertEquals(81.0, state.weight.samples.first().kg, 0.001) // (80 + 82) / 2
            assertEquals(79.0, state.weight.samples.last().kg, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildEntry(kcal: Double, date: LocalDate) = FoodEntry(
        foodName = "Test",
        brand = null,
        amountGrams = 100.0,
        kcal = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        date = date,
        timestampMs = System.currentTimeMillis(),
        mealCategory = MealCategory.SNACK,
    )
}
