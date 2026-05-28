package dev.antonlammers.macrotrac.ui.goals

import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.fake.FakeGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var viewModel: GoalsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        goalRepo = FakeGoalRepository()
        viewModel = GoalsViewModel(goalRepo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state reflects default goal`() = runTest {
        viewModel.goal.test {
            val goal = awaitItem()
            assertEquals(DailyGoal(), goal)
        }
    }

    @Test
    fun `save persists goal to repository`() = runTest {
        val newGoal = DailyGoal(kcal = 1600.0, proteinG = 130.0, carbsG = 180.0, fatG = 55.0)

        viewModel.goal.test {
            awaitItem() // default

            viewModel.save(newGoal)
            val updated = awaitItem()

            assertEquals(1600.0, updated.kcal, 0.001)
            assertEquals(130.0, updated.proteinG, 0.001)
        }
    }

    @Test
    fun `initial goal from repository is loaded`() = runTest {
        val preset = DailyGoal(kcal = 2200.0, proteinG = 160.0, carbsG = 270.0, fatG = 80.0)
        goalRepo = FakeGoalRepository(initial = preset)
        viewModel = GoalsViewModel(goalRepo)

        viewModel.goal.test {
            awaitItem() // stateIn emits initialValue = DailyGoal() first
            val actual = awaitItem() // then the value from the repository
            assertEquals(2200.0, actual.kcal, 0.001)
        }
    }
}
