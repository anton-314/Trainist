package dev.antonlammers.trainist.ui.stats

import app.cash.turbine.test
import dev.antonlammers.trainist.domain.ProgressionAdvice
import dev.antonlammers.trainist.domain.ProgressionTrend
import dev.antonlammers.trainist.domain.model.DailyGoal
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.MeasurementType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.fake.FakeBodyMeasurementRepository
import dev.antonlammers.trainist.fake.FakeExerciseCatalogRepository
import dev.antonlammers.trainist.fake.FakeFoodEntryRepository
import dev.antonlammers.trainist.fake.FakeGoalRepository
import dev.antonlammers.trainist.fake.FakeSettingsRepository
import dev.antonlammers.trainist.fake.FakeWeightRepository
import dev.antonlammers.trainist.fake.FakeWorkoutSessionRepository
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
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var measurementRepo: FakeBodyMeasurementRepository
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        foodRepo = FakeFoodEntryRepository()
        weightRepo = FakeWeightRepository()
        goalRepo = FakeGoalRepository()
        sessionRepo = FakeWorkoutSessionRepository()
        catalogRepo = FakeExerciseCatalogRepository()
        settingsRepo = FakeSettingsRepository()
        measurementRepo = FakeBodyMeasurementRepository()
        viewModel = StatsViewModel(foodRepo, weightRepo, goalRepo, sessionRepo, catalogRepo, settingsRepo, measurementRepo)
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
            var state = awaitItem()
            while (state.timeRange != TimeRange.MONTH) state = awaitItem()

            assertEquals(30, state.caloriePoints.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching to YEAR emits 12 monthly calorie points`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setTimeRange(TimeRange.YEAR)
            var state = awaitItem()
            while (state.timeRange != TimeRange.YEAR) state = awaitItem()

            assertEquals(12, state.caloriePoints.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today's food entry appears in calorie points for WEEK`() = runTest {
        val today = LocalDate.now()
        foodRepo.add(buildEntry(kcal = 500.0, date = today))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.caloriePoints.isEmpty()) state = awaitItem()

            assertEquals(500.0, state.caloriePoints.last().value, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entries outside range are not included`() = runTest {
        foodRepo.add(buildEntry(kcal = 999.0, date = LocalDate.now().minusDays(10)))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.caloriePoints.isEmpty()) state = awaitItem()

            assertEquals(7, state.caloriePoints.size)
            assertTrue(state.caloriePoints.all { it.value == 0.0 })
            cancelAndIgnoreRemainingEvents()
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

    // --- body measurements ---

    @Test
    fun `measurement card defaults to WAIST and surfaces its samples`() = runTest {
        val today = LocalDate.now()
        measurementRepo.save(today.minusDays(1), mapOf(MeasurementType.WAIST to 80.0))
        measurementRepo.save(today, mapOf(MeasurementType.WAIST to 79.0, MeasurementType.CHEST to 100.0))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.measurement.chart.samples.size < 2) state = awaitItem()

            assertEquals(MeasurementType.WAIST, state.measurement.selectedType)
            assertEquals(listOf(80.0, 79.0), state.measurement.chart.samples.map { it.cm })
            assertEquals(-1.0, state.measurement.chart.delta!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting another measurement type switches the chart`() = runTest {
        val today = LocalDate.now()
        measurementRepo.save(today, mapOf(MeasurementType.WAIST to 79.0, MeasurementType.CHEST to 100.0))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.measurement.chart.samples.isEmpty()) state = awaitItem()
            assertEquals(79.0, state.measurement.chart.current!!, 0.001)

            viewModel.setSelectedMeasurementType(MeasurementType.CHEST)
            while (state.measurement.selectedType != MeasurementType.CHEST) state = awaitItem()
            assertEquals(100.0, state.measurement.chart.current!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveMeasurements writes today's values and a null value clears an existing entry`() = runTest {
        val today = LocalDate.now()
        measurementRepo.save(today, mapOf(MeasurementType.WAIST to 79.0))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.todaysMeasurements[MeasurementType.WAIST] != 79.0) state = awaitItem()

            viewModel.saveMeasurements(mapOf(MeasurementType.WAIST to null, MeasurementType.CHEST to 101.0))
            while (state.todaysMeasurements[MeasurementType.CHEST] != 101.0) state = awaitItem()

            assertTrue(MeasurementType.WAIST !in state.todaysMeasurements)
            assertEquals(101.0, state.todaysMeasurements[MeasurementType.CHEST]!!, 0.001)
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
    fun `NEUTRAL kcal counts half toward overall clean percent`() = runTest {
        val today = LocalDate.now()
        foodRepo.add(buildEntry(kcal = 200.0, date = today, tag = FoodTag.HEALTHY))
        foodRepo.add(buildEntry(kcal = 200.0, date = today, tag = FoodTag.NEUTRAL))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.overallCleanPercent == null) state = awaitItem()

            // 200 (HEALTHY, full) + 100 (NEUTRAL, half of 200) = 300 clean of 400 total = 75%.
            assertEquals(75, state.overallCleanPercent)
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

    // --- progressive overload ---

    @Test
    fun `overload card starts empty and uninterpreted`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()

            assertTrue(state.overload.chart.points.isEmpty())
            assertEquals(ProgressionTrend.INSUFFICIENT_DATA, state.overload.trend)
            assertEquals(listOf(ProgressionAdvice.COLLECT_MORE_DATA), state.overload.advice)
            assertEquals(null, state.overload.changePercent)
        }
    }

    @Test
    fun `a couple of sessions never produce a verdict`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        sessionRepo.save(completedSession("s1", today.minusWeeks(1), "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))))
        sessionRepo.save(completedSession("s2", today, "bench", listOf(SetEntry(position = 0, weightKg = 60.0, reps = 5))))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.overload.chart.points.size < 2) state = awaitItem()

            // The index dropped hard, but two weeks is no basis for telling the user anything.
            assertEquals(ProgressionTrend.INSUFFICIENT_DATA, state.overload.trend)
            assertEquals(listOf(ProgressionAdvice.COLLECT_MORE_DATA), state.overload.advice)
            assertEquals(null, state.overload.changePercent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a steadily rising index over enough weeks reads as progress`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        // Six weeks, two sessions each, +2 kg per week on the same lift.
        (0 until 6).forEach { week ->
            val date = today.minusWeeks(5L - week)
            val weight = 100.0 + week * 2
            sessionRepo.save(completedSession("a$week", date, "bench", listOf(SetEntry(position = 0, weightKg = weight, reps = 5))))
            sessionRepo.save(completedSession("b$week", date, "bench", listOf(SetEntry(position = 0, weightKg = weight, reps = 5))))
        }

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.overload.trend == ProgressionTrend.INSUFFICIENT_DATA) state = awaitItem()

            assertEquals(ProgressionTrend.PROGRESSING, state.overload.trend)
            assertEquals(listOf(ProgressionAdvice.KEEP_GOING), state.overload.advice)
            assertEquals(10.0, state.overload.changePercent!!, 0.5) // 100 → 110 kg
            assertEquals(6, state.overload.trainingWeeks)
            assertEquals(12, state.overload.sessions)
            assertEquals(1, state.overload.chart.trackedExercises)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stagnating strength while losing weight is framed as a deficit, not a training failure`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        (0 until 6).forEach { week ->
            val date = today.minusWeeks(5L - week)
            sessionRepo.save(completedSession("a$week", date, "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))))
            sessionRepo.save(completedSession("b$week", date, "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))))
        }
        // Weekly weigh-ins, −0.5 kg per week.
        (0 until 6).forEach { week ->
            weightRepo.save(WeightEntry(date = today.minusWeeks(5L - week), weightKg = 85.0 - week * 0.5, timestampMs = week.toLong()))
        }
        val restarted = StatsViewModel(foodRepo, weightRepo, goalRepo, sessionRepo, catalogRepo, settingsRepo, measurementRepo)

        restarted.uiState.test {
            var state = awaitItem()
            while (state.overload.trend == ProgressionTrend.INSUFFICIENT_DATA) state = awaitItem()

            assertEquals(ProgressionTrend.MAINTAINING, state.overload.trend)
            assertEquals(ProgressionAdvice.EXPECTED_IN_DEFICIT, state.overload.advice.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the overload window is fixed and ignores the time range`() = runTest {
        val today = LocalDate.now()
        catalogRepo.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        // Well outside the 7-day chip range, well inside the card's own 12-week window.
        sessionRepo.save(completedSession("s1", today.minusWeeks(8), "bench", listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5))))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.overload.chart.points.isEmpty()) state = awaitItem()
            assertEquals(1, state.overload.chart.points.size)

            viewModel.setTimeRange(TimeRange.YEAR)
            while (state.timeRange != TimeRange.YEAR) state = awaitItem()

            assertEquals(1, state.overload.chart.points.size) // unchanged by the range switch
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- card order ---

    @Test
    fun `initial card order is the default`() = runTest {
        viewModel.uiState.test {
            assertEquals(StatCardType.DEFAULT_ORDER, awaitItem().cardOrder)
        }
    }

    @Test
    fun `moveCard reorders cards and persists`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.moveCard(0, 1)
            val state = awaitItem()

            val expected = StatCardType.DEFAULT_ORDER.toMutableList()
                .apply { val tmp = this[0]; this[0] = this[1]; this[1] = tmp }
            assertEquals(expected, state.cardOrder)
            assertEquals(expected, settingsRepo.statsCardOrder())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moveCard out of range is a no-op`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()

            viewModel.moveCard(0, -1)
            viewModel.moveCard(0, 99)

            expectNoEvents()
            assertEquals(StatCardType.DEFAULT_ORDER, initial.cardOrder)
        }
    }

    @Test
    fun `saved card order is restored on start`() = runTest {
        val saved = listOf(StatCardType.STRENGTH, StatCardType.CALORIES, StatCardType.WEIGHT, StatCardType.CLEAN_EATING, StatCardType.TRAINING_FREQUENCY)
        settingsRepo.setStatsCardOrder(saved)
        val restored = StatsViewModel(foodRepo, weightRepo, goalRepo, sessionRepo, catalogRepo, settingsRepo, measurementRepo)

        restored.uiState.test {
            var state = awaitItem()
            while (state.cardOrder != saved) state = awaitItem()
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
