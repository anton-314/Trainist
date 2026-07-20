package dev.antonlammers.trainist.ui.workout

import app.cash.turbine.test
import dev.antonlammers.trainist.domain.SetPerformance
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SessionExercise
import dev.antonlammers.trainist.domain.model.SetEntry
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutSession
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.fake.FakeExerciseCatalogRepository
import dev.antonlammers.trainist.fake.FakeWeightRepository
import dev.antonlammers.trainist.fake.FakeWorkoutSessionRepository
import dev.antonlammers.trainist.fake.FakeWorkoutTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class WorkoutSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessions: FakeWorkoutSessionRepository
    private lateinit var templates: FakeWorkoutTemplateRepository
    private lateinit var catalog: FakeExerciseCatalogRepository
    private lateinit var weight: FakeWeightRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessions = FakeWorkoutSessionRepository()
        templates = FakeWorkoutTemplateRepository()
        catalog = FakeExerciseCatalogRepository()
        weight = FakeWeightRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS) =
        Exercise(stableId = id, name = name, type = type, isCustom = false)

    private fun viewModel(templateId: Long = 0L) =
        WorkoutSessionViewModel(sessions, templates, catalog, weight, templateId) { FIXED_CLOCK }

    // --- start ---

    @Test
    fun `starting an empty session persists it as the active session immediately`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.exercises.isEmpty())

        val active = sessions.activeSession().first()
        assertNotNull(active)
        assertTrue(active!!.isActive)
        assertEquals(FIXED_CLOCK, active.startedAtMs)
        assertEquals(1, sessions.sessions().first().size)
    }

    @Test
    fun `starting from a template seeds its exercises with the planned number of empty sets, carrying each set's planned type`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat")))
        val templateId = templates.save(
            WorkoutTemplate(
                stableId = "tpl",
                name = "Push",
                exercises = listOf(
                    TemplateExercise("bench", 0, listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL)),
                    TemplateExercise("squat", 1, listOf(SetType.NORMAL, SetType.FAILURE)),
                ),
            ),
        )
        val vm = viewModel(templateId)
        subscribe(vm.uiState)
        advanceUntilIdle()

        val exercises = vm.uiState.value.exercises
        assertEquals(listOf("Bench Press", "Squat"), exercises.map { it.name })
        assertEquals(3, exercises[0].sets.size)
        assertEquals(2, exercises[1].sets.size)
        assertTrue(exercises.flatMap { it.sets }.all { it.weightKg == 0.0 && it.reps == 0 && !it.completed })
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL), exercises[0].sets.map { it.type })
        assertEquals(listOf(SetType.NORMAL, SetType.FAILURE), exercises[1].sets.map { it.type })

        // Links the session back to its template, so the templates list can show "last used".
        assertEquals("tpl", sessions.activeSession().first()?.templateStableId)
    }

    @Test
    fun `starting an empty (non-template) session leaves templateStableId null`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        assertNull(sessions.activeSession().first()?.templateStableId)
    }

    // --- exercises ---

    @Test
    fun `addExercise appends an exercise with one empty set`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        val exercises = vm.uiState.value.exercises
        assertEquals(1, exercises.size)
        assertEquals("Bench Press", exercises[0].name)
        assertEquals(1, exercises[0].sets.size)
    }

    @Test
    fun `removeExercise drops it`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat")))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("squat", "Squat"))
        advanceUntilIdle()

        vm.removeExercise(0)
        advanceUntilIdle()
        assertEquals(listOf("Squat"), vm.uiState.value.exercises.map { it.name })
    }

    // --- sets ---

    @Test
    fun `addSet and removeSet change the set list`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        vm.addSet(0)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.exercises[0].sets.size)

        vm.removeSet(0, 0)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.exercises[0].sets.size)
    }

    @Test
    fun `weight and reps and completed are editable per set`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        vm.setWeight(0, 0, 80.0)
        vm.setReps(0, 0, 5)
        vm.toggleSetCompleted(0, 0)
        advanceUntilIdle()

        val set = vm.uiState.value.exercises[0].sets[0]
        assertEquals(80.0, set.weightKg, 0.0)
        assertEquals(5, set.reps)
        assertTrue(set.completed)
    }

    @Test
    fun `moveSet reorders sets and reindexes positions`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()
        vm.addSet(0)
        advanceUntilIdle()
        vm.setReps(0, 0, 10) // first set marked so we can track it
        advanceUntilIdle()

        vm.moveSet(0, 0, 1)
        advanceUntilIdle()

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(10, sets[1].reps) // moved to the back
        assertEquals(listOf(0, 1), sets.map { it.position }) // positions renumbered by order
    }

    // --- set types ---

    @Test
    fun `set type is editable and persisted`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        vm.setSetType(0, 0, SetType.WARMUP)
        advanceUntilIdle()

        assertEquals(SetType.WARMUP, vm.uiState.value.exercises[0].sets[0].type)
        assertEquals(SetType.WARMUP, sessions.activeSession().first()!!.exercises[0].sets[0].type)
    }

    // --- inline history ---

    @Test
    fun `last performance of an exercise surfaces as inline-history hints`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        // A completed session from a previous day with known values.
        sessions.save(
            WorkoutSession(
                stableId = "past",
                date = java.time.LocalDate.of(2026, 7, 1),
                isActive = false,
                startedAtMs = 1L,
                endedAtMs = 2L,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = "bench",
                        position = 0,
                        sets = listOf(
                            SetEntry(position = 0, weightKg = 80.0, reps = 8),
                            SetEntry(position = 1, weightKg = 82.5, reps = 6),
                        ),
                    ),
                ),
            ),
        )
        val templateId = templates.save(
            WorkoutTemplate(
                stableId = "t", name = "Push",
                exercises = listOf(TemplateExercise("bench", 0, List(2) { SetType.NORMAL })),
            ),
        )

        val vm = viewModel(templateId)
        subscribe(vm.uiState)
        advanceUntilIdle()

        assertEquals(
            listOf(SetPerformance(80.0, 8), SetPerformance(82.5, 6)),
            vm.uiState.value.exercises.single().lastPerformance,
        )
    }

    // --- calculations (volume / 1RM) ---

    @Test
    fun `volume and estimated 1RM surface per exercise in the ui state`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        vm.setWeight(0, 0, 100.0)
        vm.setReps(0, 0, 5)
        vm.addSet(0)
        vm.setSetType(0, 1, SetType.WARMUP) // excluded from volume
        vm.setWeight(0, 1, 60.0)
        vm.setReps(0, 1, 10)
        advanceUntilIdle()

        val ex = vm.uiState.value.exercises.single()
        assertEquals(500.0, ex.volumeKg, 0.0) // only the 100×5 work set
        assertEquals(116.667, ex.estimatedOneRepMaxKg!!, 0.001) // 100 × (1 + 5/30)
    }

    @Test
    fun `bodyweight exercise volume uses the last known body weight`() = runTest {
        weight.save(WeightEntry(weightKg = 80.0, date = java.time.LocalDate.now(), timestampMs = 1L))
        catalog.upsertAll(listOf(exercise("pullup", "Pull Up", ExerciseType.BODYWEIGHT)))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("pullup", "Pull Up", ExerciseType.BODYWEIGHT))
        advanceUntilIdle()

        vm.setWeight(0, 0, 10.0) // added weight
        vm.setReps(0, 0, 8)
        advanceUntilIdle()

        // (80 body weight + 10 added) × 8 reps
        assertEquals(720.0, vm.uiState.value.exercises.single().volumeKg, 0.0)
        assertEquals(80.0, vm.uiState.value.bodyWeightKg!!, 0.0)
    }

    // --- rest timer ---
    // Commands are one-shot events on a channel-backed flow, so they are asserted with turbine
    // (which drives collection reliably across the test/main dispatchers).

    private suspend fun WorkoutSessionViewModel.startedWithOneExercise(scope: TestScope) {
        scope.advanceUntilIdle()
        addExercise(exercise("bench", "Bench Press"))
        scope.advanceUntilIdle()
    }

    private fun runningSync(name: String, totalSeconds: Int, endAtMs: Long) =
        RestCommand.Sync(name, totalSeconds, endAtMs, pausedRemainingMs = null)

    @Test
    fun `checking a set off starts the rest timer and syncs it to the service`() = runTest {
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the per-exercise rest override sets the timer duration`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press").copy(restSeconds = 120)))
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("Bench Press", 120, FIXED_CLOCK + 120_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pause syncs the frozen remaining time, resume re-anchors the end instant`() = runTest {
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            vm.pauseRest()
            assertEquals(RestCommand.Sync("bench", 180, FIXED_CLOCK + 180_000, 180_000L), awaitItem())
            vm.resumeRest()
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adjusting a running timer moves the synced end instant`() = runTest {
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            vm.adjustRest(15)
            assertEquals(runningSync("bench", 195, FIXED_CLOCK + 195_000), awaitItem())
            vm.adjustRest(-15)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adjusting a paused timer moves the synced remaining time`() = runTest {
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            vm.pauseRest()
            assertEquals(RestCommand.Sync("bench", 180, FIXED_CLOCK + 180_000, 180_000L), awaitItem())
            vm.adjustRest(15)
            assertEquals(RestCommand.Sync("bench", 195, FIXED_CLOCK + 180_000, 195_000L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `skipping stops the timer`() = runTest {
        val vm = viewModel()
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            vm.skipRest()
            assertEquals(RestCommand.Stop, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a countdown reaching zero clears the timer and its anchor without a command`() = runTest {
        var now = FIXED_CLOCK
        val vm = WorkoutSessionViewModel(sessions, templates, catalog, weight, 0L) { now }
        vm.startedWithOneExercise(this)
        subscribe(vm.restTimer)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            runCurrent() // first tick observes the countdown still running
            now = FIXED_CLOCK + 181_000 // wall clock passes the end...
            advanceTimeBy(1_100) // ...and the next tick sees the expiry
            // The alert belongs to RestTimerService, which was handed the end instant with the Sync
            // above — the lifecycle-bound countdown here only clears the UI state.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.restTimer.value)
        assertNull(sessions.activeSession().first()!!.restExerciseStableId)
    }

    @Test
    fun `a timer already expired when the countdown becomes visible is dropped silently`() = runTest {
        var now = FIXED_CLOCK
        val vm = WorkoutSessionViewModel(sessions, templates, catalog, weight, 0L) { now }
        vm.startedWithOneExercise(this)
        vm.restCommands.test {
            vm.toggleSetCompleted(0, 0)
            assertEquals(runningSync("bench", 180, FIXED_CLOCK + 180_000), awaitItem())
            now = FIXED_CLOCK + 181_000 // expires while the screen is away...
            subscribe(vm.restTimer) // ...then the ticking starts: the first value is already <= 0
            advanceTimeBy(2_000)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.restTimer.value)
    }

    // --- rest timer survives leaving/resuming the session (persisted anchor) ---

    @Test
    fun `resuming an active session restores a paused rest timer from its persisted anchor`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        sessions.save(
            WorkoutSession(
                stableId = "running",
                date = java.time.LocalDate.now(),
                isActive = true,
                startedAtMs = 500L,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = "bench",
                        position = 0,
                        sets = listOf(SetEntry(position = 0, weightKg = 80.0, reps = 5, completed = true)),
                    ),
                ),
                restExerciseStableId = "bench",
                restTotalSeconds = 180,
                restEndAtMs = FIXED_CLOCK + 60_000,
                restPausedRemainingMs = 45_000L,
            ),
        )

        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.restTimer)
        advanceUntilIdle()

        val restored = vm.restTimer.value
        assertNotNull(restored)
        assertEquals("Bench Press", restored!!.exerciseName)
        assertEquals(180, restored.totalSeconds)
        assertEquals(45, restored.remainingSeconds)
        assertTrue(restored.isPaused)
    }

    @Test
    fun `resuming a still-running rest re-syncs it, so the service is running even after a process death`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        sessions.save(
            WorkoutSession(
                stableId = "running",
                date = java.time.LocalDate.now(),
                isActive = true,
                startedAtMs = 500L,
                restExerciseStableId = "bench",
                restTotalSeconds = 180,
                restEndAtMs = FIXED_CLOCK + 60_000,
            ),
        )

        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.restCommands.test {
            assertEquals(runningSync("Bench Press", 180, FIXED_CLOCK + 60_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resuming drops an already-finished persisted rest timer and clears its anchor`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        sessions.save(
            WorkoutSession(
                stableId = "running",
                date = java.time.LocalDate.now(),
                isActive = true,
                startedAtMs = 500L,
                restExerciseStableId = "bench",
                restTotalSeconds = 180,
                restEndAtMs = FIXED_CLOCK - 1L,
            ),
        )

        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.restTimer)
        advanceUntilIdle()

        assertNull(vm.restTimer.value)
        assertNull(sessions.activeSession().first()!!.restExerciseStableId)
    }

    @Test
    fun `setExerciseRest persists the override on the exercise`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel()
        advanceUntilIdle()

        vm.setExerciseRest("bench", 150)
        advanceUntilIdle()

        assertEquals(150, catalog.exercise("bench").first()!!.restSeconds)
    }

    // --- continuous persistence ---

    @Test
    fun `every change is persisted to the single active session without duplicating rows`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addSet(0)
        vm.setWeight(0, 0, 60.0)
        advanceUntilIdle()

        assertEquals(1, sessions.sessions().first().size) // no duplicate inserts
        val persisted = sessions.activeSession().first()!!
        assertEquals(1, persisted.exercises.size)
        assertEquals(2, persisted.exercises[0].sets.size)
        assertEquals(60.0, persisted.exercises[0].sets[0].weightKg, 0.0)
    }

    // --- resume ---

    @Test
    fun `an existing active session is resumed and the template argument is ignored`() = runTest {
        catalog.upsertAll(listOf(exercise("row", "Barbell Row")))
        // A running session already persisted from a previous app launch.
        sessions.save(
            dev.antonlammers.trainist.domain.model.WorkoutSession(
                stableId = "running",
                date = java.time.LocalDate.now(),
                isActive = true,
                startedAtMs = 500L,
                exercises = listOf(
                    dev.antonlammers.trainist.domain.model.SessionExercise(
                        exerciseStableId = "row",
                        position = 0,
                        sets = listOf(dev.antonlammers.trainist.domain.model.SetEntry(position = 0, weightKg = 40.0, reps = 8)),
                    ),
                ),
            ),
        )
        val templateId = templates.save(
            WorkoutTemplate(
                stableId = "t", name = "Other",
                exercises = listOf(TemplateExercise("row", 0, List(5) { SetType.NORMAL })),
            ),
        )

        val vm = viewModel(templateId)
        subscribe(vm.uiState)
        advanceUntilIdle()

        // Resumed the running session (1 set), not the template (which would have 5).
        assertEquals(1, vm.uiState.value.exercises.size)
        assertEquals(1, vm.uiState.value.exercises[0].sets.size)
        assertEquals(1, sessions.sessions().first().size)
    }

    // --- finish / discard ---

    @Test
    fun `finish marks the session completed and keeps it in history`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()
        vm.setWeight(0, 0, 80.0)
        vm.setReps(0, 0, 5)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.finished.value)
        assertNull(sessions.activeSession().first()) // no longer active
        val all = sessions.sessions().first()
        assertEquals(1, all.size)
        assertFalse(all[0].isActive)
        assertEquals(FIXED_CLOCK, all[0].endedAtMs)
    }

    @Test
    fun `finish deletes a session with zero volume instead of keeping it in history`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle() // set added with default weight 0.0 / reps 0 — never logged

        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.finished.value)
        assertTrue(sessions.sessions().first().isEmpty())
    }

    @Test
    fun `finish deletes an empty session with no exercises`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()

        assertTrue(vm.finished.value)
        assertTrue(sessions.sessions().first().isEmpty())
    }

    @Test
    fun `discard deletes the session entirely`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()

        vm.discard()
        advanceUntilIdle()

        assertTrue(vm.finished.value)
        assertTrue(sessions.sessions().first().isEmpty())
    }

    // --- adopting inline-history placeholders on check-off ---

    private suspend fun saveCompletedPast(exerciseStableId: String, sets: List<Pair<Double, Int>>) {
        sessions.save(
            WorkoutSession(
                stableId = "past-$exerciseStableId",
                date = java.time.LocalDate.of(2026, 7, 1),
                isActive = false,
                startedAtMs = 1L,
                endedAtMs = 2L,
                exercises = listOf(
                    SessionExercise(
                        exerciseStableId = exerciseStableId,
                        position = 0,
                        sets = sets.mapIndexed { i, (w, r) ->
                            SetEntry(position = i, weightKg = w, reps = r, completed = true)
                        },
                    ),
                ),
            ),
        )
    }

    private suspend fun templateWith(exerciseStableId: String, setCount: Int): Long = templates.save(
        WorkoutTemplate(
            stableId = "t",
            name = "Push",
            exercises = listOf(TemplateExercise(exerciseStableId, 0, List(setCount) { SetType.NORMAL })),
        ),
    )

    @Test
    fun `checking an empty set off adopts the inline-history placeholder as its values`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        saveCompletedPast("bench", listOf(80.0 to 8, 82.5 to 6))
        val vm = viewModel(templateWith("bench", 2))
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.toggleSetCompleted(0, 0)
        advanceUntilIdle()

        val set0 = vm.uiState.value.exercises.single().sets[0]
        assertTrue(set0.completed)
        assertEquals(80.0, set0.weightKg, 0.0)
        assertEquals(8, set0.reps)
    }

    @Test
    fun `checking a set off does not overwrite values the user already entered`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        saveCompletedPast("bench", listOf(80.0 to 8, 82.5 to 6))
        val vm = viewModel(templateWith("bench", 2))
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.setWeight(0, 0, 100.0)
        vm.setReps(0, 0, 3)
        advanceUntilIdle()
        vm.toggleSetCompleted(0, 0)
        advanceUntilIdle()

        val set0 = vm.uiState.value.exercises.single().sets[0]
        assertEquals(100.0, set0.weightKg, 0.0)
        assertEquals(3, set0.reps)
    }

    @Test
    fun `no inline history means an empty set stays empty when checked off`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel(templateWith("bench", 1))
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.toggleSetCompleted(0, 0)
        advanceUntilIdle()

        val set0 = vm.uiState.value.exercises.single().sets[0]
        assertTrue(set0.completed)
        assertEquals(0.0, set0.weightKg, 0.0)
        assertEquals(0, set0.reps)
    }

    // --- offering to update the template on finish ---

    /** Logs [exerciseIndex]'s sets with real weight/reps and checks each off (so volume > 0). */
    private suspend fun WorkoutSessionViewModel.logAndCompleteAll(scope: TestScope, exerciseIndex: Int) {
        val count = uiState.value.exercises[exerciseIndex].sets.size
        repeat(count) { setIndex ->
            setWeight(exerciseIndex, setIndex, 80.0)
            setReps(exerciseIndex, setIndex, 8)
        }
        scope.advanceUntilIdle()
        repeat(count) { setIndex -> toggleSetCompleted(exerciseIndex, setIndex) }
        scope.advanceUntilIdle()
    }

    @Test
    fun `finishing a template session with an added set offers a template update, confirm persists it`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel(templateWith("bench", 2))
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()

        vm.addSet(0) // 2 planned + 1 added = 3
        advanceUntilIdle()
        vm.logAndCompleteAll(this, 0)

        vm.finish()
        advanceUntilIdle()

        // The screen stays open on the update dialog instead of popping.
        assertFalse(vm.finished.value)
        val merged = vm.pendingTemplateUpdate.value
        assertNotNull(merged)
        assertEquals(3, merged!!.exercises.single().setTypes.size)

        vm.confirmTemplateUpdate()
        advanceUntilIdle()
        assertTrue(vm.finished.value)
        assertNull(vm.pendingTemplateUpdate.value)
        val stored = templates.templates().first().single { it.stableId == "t" }
        assertEquals(3, stored.exercises.single().setTypes.size)
    }

    @Test
    fun `dismissing the template update leaves the template unchanged and finishes`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel(templateWith("bench", 2))
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()

        vm.addSet(0)
        advanceUntilIdle()
        vm.logAndCompleteAll(this, 0)

        vm.finish()
        advanceUntilIdle()
        assertNotNull(vm.pendingTemplateUpdate.value)

        vm.dismissTemplateUpdate()
        advanceUntilIdle()
        assertTrue(vm.finished.value)
        assertNull(vm.pendingTemplateUpdate.value)
        val stored = templates.templates().first().single { it.stableId == "t" }
        assertEquals(2, stored.exercises.single().setTypes.size)
    }

    @Test
    fun `finishing a template session with no structural change offers no update`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val vm = viewModel(templateWith("bench", 2))
        subscribe(vm.uiState)
        subscribe(vm.finished)
        advanceUntilIdle()

        vm.logAndCompleteAll(this, 0) // exactly the two planned NORMAL sets

        vm.finish()
        advanceUntilIdle()
        assertTrue(vm.finished.value)
        assertNull(vm.pendingTemplateUpdate.value)
    }

    private companion object {
        const val FIXED_CLOCK = 1_000L
    }
}
