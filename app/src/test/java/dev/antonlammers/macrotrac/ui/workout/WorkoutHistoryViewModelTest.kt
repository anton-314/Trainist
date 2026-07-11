package dev.antonlammers.macrotrac.ui.workout

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.SetType
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
import dev.antonlammers.macrotrac.fake.FakeWeightRepository
import dev.antonlammers.macrotrac.fake.FakeWorkoutSessionRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessions: FakeWorkoutSessionRepository
    private lateinit var catalog: FakeExerciseCatalogRepository
    private lateinit var weight: FakeWeightRepository

    private val today = LocalDate.of(2026, 7, 15)
    private val clockMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessions = FakeWorkoutSessionRepository()
        catalog = FakeExerciseCatalogRepository()
        weight = FakeWeightRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun viewModel() = WorkoutHistoryViewModel(sessions, catalog, weight) { clockMs }

    private suspend fun saveSession(
        stableId: String,
        date: LocalDate,
        exerciseStableId: String = "bench",
        sets: List<SetEntry> = listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5)),
        isActive: Boolean = false,
    ): Long = sessions.save(
        WorkoutSession(
            stableId = stableId,
            date = date,
            isActive = isActive,
            startedAtMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endedAtMs = if (isActive) null else 1L,
            exercises = listOf(SessionExercise(exerciseStableId = exerciseStableId, position = 0, sets = sets)),
        ),
    )

    // --- calendar grouping ---

    @Test
    fun `training days of the displayed month are marked, other months excluded`() = runTest {
        saveSession("a", LocalDate.of(2026, 7, 2))
        saveSession("b", LocalDate.of(2026, 7, 20))
        saveSession("c", LocalDate.of(2026, 6, 30)) // different month
        saveSession("active", today, isActive = true) // active session is not history

        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        assertEquals(
            setOf(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 20)),
            vm.uiState.value.trainingDays,
        )
    }

    @Test
    fun `selecting a day shows its sessions with volume and a PR badge`() = runTest {
        catalog.upsertAll(listOf(Exercise("bench", "Bench Press", ExerciseType.WEIGHT_REPS, isCustom = false)))
        saveSession("a", LocalDate.of(2026, 7, 2), sets = listOf(SetEntry(position = 0, weightKg = 100.0, reps = 5)))

        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()

        vm.selectDate(LocalDate.of(2026, 7, 2))
        advanceUntilIdle()

        val session = vm.uiState.value.selectedSessions.single()
        val exercise = session.exercises.single()
        assertEquals("Bench Press", exercise.name)
        assertEquals(500.0, exercise.volumeKg, 0.0)
        assertEquals(500.0, session.totalVolumeKg, 0.0)
        assertTrue(exercise.isPersonalRecord) // first ever → PR
    }

    // --- editing a past session ---

    @Test
    fun `editing weight, reps and set-type of a past session persists`() = runTest {
        val id = saveSession("a", LocalDate.of(2026, 7, 2))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.selectDate(LocalDate.of(2026, 7, 2))
        advanceUntilIdle()

        vm.setWeight(id, 0, 0, 110.0)
        vm.setReps(id, 0, 0, 6)
        vm.setSetType(id, 0, 0, SetType.DROP)
        advanceUntilIdle()

        val set = sessions.session(id).first()!!.exercises[0].sets[0]
        assertEquals(110.0, set.weightKg, 0.0)
        assertEquals(6, set.reps)
        assertEquals(SetType.DROP, set.type)
    }

    @Test
    fun `adding and removing a set of a past session persists and reindexes`() = runTest {
        val id = saveSession("a", LocalDate.of(2026, 7, 2))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.selectDate(LocalDate.of(2026, 7, 2))
        advanceUntilIdle()

        vm.addSet(id, 0)
        advanceUntilIdle()
        assertEquals(2, sessions.session(id).first()!!.exercises[0].sets.size)

        vm.removeSet(id, 0, 0)
        advanceUntilIdle()
        val sets = sessions.session(id).first()!!.exercises[0].sets
        assertEquals(1, sets.size)
        assertEquals(listOf(0), sets.map { it.position })
    }

    // --- delete with undo ---

    @Test
    fun `deletePending hides the session, undo restores it, confirm removes it`() = runTest {
        val id = saveSession("a", LocalDate.of(2026, 7, 2))
        val vm = viewModel()
        subscribe(vm.uiState)
        advanceUntilIdle()
        vm.selectDate(LocalDate.of(2026, 7, 2))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedSessions.size)

        vm.deletePending(id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedSessions.isEmpty()) // hidden during undo window
        assertFalse(vm.uiState.value.trainingDays.contains(LocalDate.of(2026, 7, 2)))

        vm.undoDelete(id)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.selectedSessions.size) // restored

        vm.deletePending(id)
        vm.confirmDelete(id)
        advanceUntilIdle()
        assertTrue(sessions.sessions().first().isEmpty()) // gone for good
    }
}
