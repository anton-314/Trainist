package dev.antonlammers.macrotrac.ui.workout

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
import dev.antonlammers.macrotrac.fake.FakeWorkoutSessionRepository
import dev.antonlammers.macrotrac.fake.FakeWorkoutTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class WorkoutSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessions: FakeWorkoutSessionRepository
    private lateinit var templates: FakeWorkoutTemplateRepository
    private lateinit var catalog: FakeExerciseCatalogRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessions = FakeWorkoutSessionRepository()
        templates = FakeWorkoutTemplateRepository()
        catalog = FakeExerciseCatalogRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS) =
        Exercise(stableId = id, name = name, type = type, isCustom = false)

    private fun viewModel(templateId: Long = 0L) =
        WorkoutSessionViewModel(sessions, templates, catalog, templateId) { FIXED_CLOCK }

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
    fun `starting from a template seeds its exercises with the planned number of empty sets`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat")))
        val templateId = templates.save(
            WorkoutTemplate(
                stableId = "tpl",
                name = "Push",
                exercises = listOf(
                    TemplateExercise("bench", 0, 3),
                    TemplateExercise("squat", 1, 2),
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
    fun `moveSetDown reorders sets and reindexes positions`() = runTest {
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.addExercise(exercise("bench", "Bench Press"))
        advanceUntilIdle()
        vm.addSet(0)
        advanceUntilIdle()
        vm.setReps(0, 0, 10) // first set marked so we can track it
        advanceUntilIdle()

        vm.moveSetDown(0, 0)
        advanceUntilIdle()

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(10, sets[1].reps) // moved to the back
        assertEquals(listOf(0, 1), sets.map { it.position }) // positions renumbered by order
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
            dev.antonlammers.macrotrac.domain.model.WorkoutSession(
                stableId = "running",
                date = java.time.LocalDate.now(),
                isActive = true,
                startedAtMs = 500L,
                exercises = listOf(
                    dev.antonlammers.macrotrac.domain.model.SessionExercise(
                        exerciseStableId = "row",
                        position = 0,
                        sets = listOf(dev.antonlammers.macrotrac.domain.model.SetEntry(position = 0, weightKg = 40.0, reps = 8)),
                    ),
                ),
            ),
        )
        val templateId = templates.save(
            WorkoutTemplate(stableId = "t", name = "Other", exercises = listOf(TemplateExercise("row", 0, 5))),
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

    private companion object {
        const val FIXED_CLOCK = 1_000L
    }
}
