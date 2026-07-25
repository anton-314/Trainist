package dev.antonlammers.trainist.ui.bmr

import dev.antonlammers.trainist.domain.BmrCalculator
import dev.antonlammers.trainist.domain.MacroCalculator
import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.BmrProfile
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WeightGoal
import dev.antonlammers.trainist.fake.FakeGoalRepository
import dev.antonlammers.trainist.fake.FakeWeightRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BmrCalculatorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var weightRepo: FakeWeightRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        goalRepo = FakeGoalRepository()
        weightRepo = FakeWeightRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init prefills from an existing profile and the most recent weight entry`() = runTest {
        val profile = BmrProfile(BiologicalSex.FEMALE, ageYears = 28, heightCm = 168.0, activityLevel = ActivityLevel.LIGHT)
        goalRepo = FakeGoalRepository(initial = DailyGoal(bmrProfile = profile))
        weightRepo.save(WeightEntry(weightKg = 60.0, date = LocalDate.of(2026, 1, 1), timestampMs = 1))
        weightRepo.save(WeightEntry(weightKg = 62.5, date = LocalDate.of(2026, 3, 1), timestampMs = 2))
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(BiologicalSex.FEMALE, state.sex)
        assertEquals("28", state.ageInput)
        assertEquals("168", state.heightInput)
        assertEquals(ActivityLevel.LIGHT, state.activityLevel)
        // Most recent by date (2026-03-01), not insertion order.
        assertEquals("62.5", state.weightInput)
    }

    @Test
    fun `init leaves everything blank when nothing is saved`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.sex)
        assertEquals("", state.ageInput)
        assertEquals("", state.heightInput)
        assertEquals("", state.weightInput)
        assertNull(state.activityLevel)
    }

    @Test
    fun `canProceed rejects out-of-range age, height and weight`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)
        advanceUntilIdle()

        viewModel.setSex(BiologicalSex.MALE)
        viewModel.goNext() // -> AGE

        viewModel.setAgeInput("5")
        assertFalse(viewModel.canProceed())
        viewModel.setAgeInput("200")
        assertFalse(viewModel.canProceed())
        viewModel.setAgeInput("30")
        assertTrue(viewModel.canProceed())
        viewModel.goNext() // -> HEIGHT

        viewModel.setHeightInput("50")
        assertFalse(viewModel.canProceed())
        viewModel.setHeightInput("300")
        assertFalse(viewModel.canProceed())
        viewModel.setHeightInput("180")
        assertTrue(viewModel.canProceed())
        viewModel.goNext() // -> WEIGHT

        viewModel.setWeightInput("10")
        assertFalse(viewModel.canProceed())
        viewModel.setWeightInput("500")
        assertFalse(viewModel.canProceed())
        viewModel.setWeightInput("80")
        assertTrue(viewModel.canProceed())
    }

    @Test
    fun `goNext is a no-op when the current step is invalid`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)
        advanceUntilIdle()

        viewModel.goNext() // SEX not chosen yet

        assertEquals(BmrStep.SEX, viewModel.uiState.value.step)
    }

    @Test
    fun `goNext persists the profile once leaving ACTIVITY, before GOAL is touched`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)
        advanceUntilIdle()

        viewModel.setSex(BiologicalSex.MALE)
        viewModel.goNext() // -> AGE
        viewModel.setAgeInput("30")
        viewModel.goNext() // -> HEIGHT
        viewModel.setHeightInput("180")
        viewModel.goNext() // -> WEIGHT
        viewModel.setWeightInput("80")
        viewModel.goNext() // -> ACTIVITY
        viewModel.setActivityLevel(ActivityLevel.MODERATE)
        viewModel.goNext() // -> GOAL, persists here
        advanceUntilIdle()

        assertEquals(BmrStep.GOAL, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.goal)
        assertEquals(
            BmrProfile(BiologicalSex.MALE, ageYears = 30, heightCm = 180.0, activityLevel = ActivityLevel.MODERATE),
            goalRepo.goal().first().bmrProfile,
        )
    }

    @Test
    fun `goBack returns false at SEX and true otherwise`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)
        advanceUntilIdle()

        assertFalse(viewModel.goBack())
        assertEquals(BmrStep.SEX, viewModel.uiState.value.step)

        viewModel.setSex(BiologicalSex.MALE)
        viewModel.goNext() // -> AGE
        assertTrue(viewModel.goBack())
        assertEquals(BmrStep.SEX, viewModel.uiState.value.step)
    }

    @Test
    fun `result is null until every input is valid, then matches the calculator composition`() = runTest {
        val viewModel = BmrCalculatorViewModel(goalRepo, weightRepo)
        advanceUntilIdle()

        assertNull(viewModel.result())

        viewModel.setSex(BiologicalSex.MALE)
        viewModel.setAgeInput("30")
        viewModel.setHeightInput("180")
        viewModel.setWeightInput("80")
        viewModel.setActivityLevel(ActivityLevel.MODERATE)
        assertNull(viewModel.result()) // goal still missing

        viewModel.setGoal(WeightGoal.LOSE)
        val result = viewModel.result()!!

        val expectedBmr = BmrCalculator.bmrKcal(BiologicalSex.MALE, 80.0, 180.0, 30)
        val expectedTdee = BmrCalculator.tdeeKcal(expectedBmr, ActivityLevel.MODERATE)
        val expectedKcal = BmrCalculator.goalKcal(expectedTdee, expectedBmr, WeightGoal.LOSE)
        val expectedProtein = MacroCalculator.recommendedProteinG(80.0)
        val expectedFat = MacroCalculator.recommendedFatG(80.0)
        val expectedCarbs = MacroCalculator.carbsFromKcalAndMacros(expectedKcal, expectedProtein, expectedFat)

        assertEquals(expectedBmr, result.bmrKcal, 0.001)
        assertEquals(expectedTdee, result.tdeeKcal, 0.001)
        assertEquals(expectedKcal, result.goalKcal, 0.001)
        assertEquals(expectedProtein, result.proteinG, 0.001)
        assertEquals(expectedCarbs, result.carbsG, 0.001)
        assertEquals(expectedFat, result.fatG, 0.001)
        assertEquals(
            BmrCalculator.weeklyWeightChangeKg(expectedTdee, expectedKcal),
            result.weeklyWeightChangeKg,
            0.001,
        )
    }

    @Test
    fun `the weekly rate follows the chosen goal in sign and stays moderate`() = runTest {
        val viewModel = BmrCalculatorViewModel(FakeGoalRepository(), FakeWeightRepository())
        advanceUntilIdle()
        viewModel.setSex(BiologicalSex.FEMALE)
        viewModel.setAgeInput("25")
        viewModel.setHeightInput("165")
        viewModel.setWeightInput("60")
        viewModel.setActivityLevel(ActivityLevel.MODERATE)

        viewModel.setGoal(WeightGoal.LOSE)
        val losing = viewModel.result()!!.weeklyWeightChangeKg
        viewModel.setGoal(WeightGoal.MAINTAIN)
        val maintaining = viewModel.result()!!.weeklyWeightChangeKg
        viewModel.setGoal(WeightGoal.GAIN)
        val gaining = viewModel.result()!!.weeklyWeightChangeKg

        assertTrue(losing < 0.0)
        assertEquals(0.0, maintaining, 0.001)
        assertTrue(gaining > 0.0)
        // A 60 kg user: 0.75 % of body weight per week is the upper end worth recommending.
        assertTrue("$losing kg/week is too aggressive", losing > -0.45)
    }
}
