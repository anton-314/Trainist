package dev.antonlammers.macrotrac.ui.stats

import app.cash.turbine.test
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.FoodTag
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.WeightEntry
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
import dev.antonlammers.macrotrac.fake.FakeFoodEntryRepository
import dev.antonlammers.macrotrac.fake.FakeGoalRepository
import dev.antonlammers.macrotrac.fake.FakeWeightRepository
import dev.antonlammers.macrotrac.fake.FakeWorkoutSessionRepository
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
    private lateinit var sessionRepo: FakeWorkoutSessionRepository
    private lateinit var catalogRepo: FakeExerciseCatalogRepository
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        foodRepo = FakeFoodEntryRepository()
        weightRepo = FakeWeightRepository()
        goalRepo = FakeGoalRepository()
        sessionRepo = FakeWorkoutSessionRepository()
        catalogRepo = FakeExerciseCatalogRepository()
        viewModel = StatsViewModel(foodRepo, weightRepo, goalRepo, sessionRepo, catalogRepo)
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

    @Test
    fun `clean points and overall percent reflect healthy share`() = runTest {
        val today = LocalDate.now()
        foodRepo.add(buildEntry(kcal = 300.0, date = today, tag = FoodTag.HEALTHY))
        foodRepo.add(buildEntry(kcal = 100.0, date = today, tag = FoodTag.UNHEALTHY))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.overallCleanPercent == null) state = awaitItem()

            // 300 clean of 400 total = 75%.
            assertEquals(75, state.overallCleanPercent)
            assertEquals(75.0, state.cleanPoints.last().value, 0.001)
            assertEquals(7, state.cleanPoints.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overall clean percent is null without entries`() = runTest {
        viewModel.uiState.test {
            assertEquals(null, awaitItem().overallCleanPercent)
        }
    }

    // --- training charts ---

    @Test
    fun `frequency counts completed sessions per bucket and ignores active sessions`() = runTest {
        val today = LocalDate.now()
        sessionRepo.save(completedSession("s1", today))
        sessionRepo.save(completedSession("s2", today))
        sessionRepo.save(activeSession("s3", today))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.frequencyPoints.sumOf { it.value } < 2.0) state = awaitItem()

            assertEquals(7, state.frequencyPoints.size) // WEEK → 7 daily buckets
            assertEquals(2.0, state.frequencyPoints.last().value, 0.001) // two completed today
            assertEquals(2.0, state.frequencyPoints.sumOf { it.value }, 0.001) // active session excluded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `strength chart surfaces the selected exercise's estimated 1RM over time`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        sessionRepo.save(
            completedSession("s1", today, "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))),
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.strength.samples.isEmpty()) state = awaitItem()

            assertEquals(listOf("Bench Press"), state.strengthExercises.map { it.name })
            assertEquals("bench", state.selectedExerciseId) // first option auto-selected
            assertEquals(116.667, state.strength.samples.last().estimatedOneRepMaxKg, 0.01) // 100×(1+5/30)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting another exercise switches the strength chart`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(
            listOf(
                Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false),
                Exercise("squat", "Squat", ExerciseType.WEIGHT_REPS, isCustom = false),
            ),
        )
        sessionRepo.save(completedSession("s1", today, "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))))
        sessionRepo.save(completedSession("s2", today, "squat", listOf(SetEntry(position = 0, weightKg = 140.0, reps = 5))))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.selectedExerciseId == null) state = awaitItem()
            assertEquals("bench", state.selectedExerciseId) // alphabetical first

            viewModel.setSelectedExercise("squat")
            while (state.selectedExerciseId != "squat") state = awaitItem()
            assertEquals(163.333, state.strength.samples.last().estimatedOneRepMaxKg, 0.01) // 140×(1+5/30)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun completedSession(
        stableId: String,
        date: LocalDate,
        exerciseStableId: String = "bench",
        sets: List<SetEntry> = listOf(SetEntry(position = 0, weightKg = 80.0, reps = 5)),
    ) = WorkoutSession(
        stableId = stableId,
        date = date,
        isActive = false,
        startedAtMs = 1L,
        endedAtMs = 2L,
        exercises = listOf(SessionExercise(exerciseStableId = exerciseStableId, position = 0, sets = sets)),
    )

    private fun activeSession(stableId: String, date: LocalDate) = WorkoutSession(
        stableId = stableId,
        date = date,
        isActive = true,
        startedAtMs = 1L,
    )

    private fun buildEntry(kcal: Double, date: LocalDate, tag: FoodTag = FoodTag.NONE) = FoodEntry(
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
        tag = tag,
    )
}
