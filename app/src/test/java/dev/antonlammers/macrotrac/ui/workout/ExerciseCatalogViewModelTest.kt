package dev.antonlammers.macrotrac.ui.workout

import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.Mechanic
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeExerciseCatalogRepository
    private lateinit var viewModel: ExerciseCatalogViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeExerciseCatalogRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Keep a `WhileSubscribed` StateFlow hot so it computes real values, then read `.value`. */
    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun catalog(id: String, name: String, muscle: String, equipment: String) = Exercise(
        stableId = id, name = name, type = ExerciseType.WEIGHT_REPS, isCustom = false,
        primaryMuscles = listOf(muscle), equipment = equipment,
    )

    private suspend fun seed(vararg exercises: Exercise) = repo.upsertAll(exercises.toList())

    // --- search ---

    @Test
    fun `exercises are sorted by name`() = runTest {
        seed(
            catalog("b", "Squat", "quadriceps", "barbell"),
            catalog("a", "Bench Press", "chest", "barbell"),
        )
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.exercises)
        advanceUntilIdle()
        assertEquals(listOf("Bench Press", "Squat"), viewModel.exercises.value.map { it.name })
    }

    @Test
    fun `query filters by name case-insensitively`() = runTest {
        seed(
            catalog("a", "Bench Press", "chest", "barbell"),
            catalog("b", "Squat", "quadriceps", "barbell"),
        )
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.exercises)
        viewModel.onQueryChange("bench")
        advanceUntilIdle()
        assertEquals(listOf("Bench Press"), viewModel.exercises.value.map { it.name })
    }

    // --- filters ---

    @Test
    fun `muscle and equipment filters narrow the list`() = runTest {
        seed(
            catalog("a", "Bench Press", "chest", "barbell"),
            catalog("b", "Push-Up", "chest", "body only"),
            catalog("c", "Squat", "quadriceps", "barbell"),
        )
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.exercises)

        viewModel.onMuscleSelected("chest")
        advanceUntilIdle()
        assertEquals(listOf("Bench Press", "Push-Up"), viewModel.exercises.value.map { it.name })

        viewModel.onEquipmentSelected("barbell")
        advanceUntilIdle()
        assertEquals(listOf("Bench Press"), viewModel.exercises.value.map { it.name })
    }

    @Test
    fun `selecting the active muscle filter clears it`() = runTest {
        seed(catalog("a", "Bench Press", "chest", "barbell"))
        viewModel = ExerciseCatalogViewModel(repo)
        viewModel.onMuscleSelected("chest")
        advanceUntilIdle()
        assertEquals("chest", viewModel.uiState.value.muscle)
        viewModel.onMuscleSelected("chest")
        assertNull(viewModel.uiState.value.muscle)
    }

    @Test
    fun `filterOptions expose distinct sorted muscles and equipment`() = runTest {
        seed(
            catalog("a", "Bench Press", "chest", "barbell"),
            catalog("b", "Push-Up", "chest", "body only"),
            catalog("c", "Squat", "quadriceps", "barbell"),
        )
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.filterOptions)
        advanceUntilIdle()
        val options = viewModel.filterOptions.value
        assertEquals(listOf("chest", "quadriceps"), options.muscles)
        assertEquals(listOf("barbell", "body only"), options.equipment)
    }

    // --- custom CRUD ---

    @Test
    fun `saveCustomExercise creates a custom entry with a generated stable id`() = runTest {
        viewModel = ExerciseCatalogViewModel(repo)
        viewModel.saveCustomExercise(
            stableId = null,
            name = "  My Row  ",
            type = ExerciseType.BODYWEIGHT,
            primaryMuscles = listOf("back"),
            equipment = "body only",
            mechanic = Mechanic.COMPOUND,
            instructions = listOf("Pull up"),
        )
        advanceUntilIdle()
        val saved = repo.exercises().first()
        assertEquals(1, saved.size)
        val ex = saved.first()
        assertEquals("My Row", ex.name)
        assertTrue(ex.isCustom)
        assertTrue(ex.stableId.isNotBlank())
        assertEquals(ExerciseType.BODYWEIGHT, ex.type)
    }

    @Test
    fun `saveCustomExercise with existing id updates in place`() = runTest {
        viewModel = ExerciseCatalogViewModel(repo)
        repo.upsertAll(listOf(Exercise("my-1", "Old", ExerciseType.WEIGHT_REPS, isCustom = true)))
        viewModel.saveCustomExercise(
            stableId = "my-1",
            name = "New",
            type = ExerciseType.WEIGHT_REPS,
            primaryMuscles = emptyList(),
            equipment = null,
            mechanic = null,
            instructions = emptyList(),
        )
        advanceUntilIdle()
        val all = repo.exercises().first()
        assertEquals(1, all.size)
        assertEquals("New", all.first().name)
    }

    @Test
    fun `blank name is ignored`() = runTest {
        viewModel = ExerciseCatalogViewModel(repo)
        viewModel.saveCustomExercise(
            stableId = null, name = "   ", type = ExerciseType.WEIGHT_REPS,
            primaryMuscles = emptyList(), equipment = null, mechanic = null, instructions = emptyList(),
        )
        advanceUntilIdle()
        assertTrue(repo.exercises().first().isEmpty())
    }

    @Test
    fun `deferred delete hides then removes the exercise`() = runTest {
        repo.upsertAll(listOf(Exercise("my-1", "Custom", ExerciseType.WEIGHT_REPS, isCustom = true)))
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.exercises)
        advanceUntilIdle()
        val exercise = repo.exercises().first().first()

        assertEquals(1, viewModel.exercises.value.size)

        viewModel.deletePending(exercise)
        advanceUntilIdle()
        assertTrue(viewModel.exercises.value.isEmpty()) // hidden while snackbar shows

        viewModel.confirmDelete(exercise)
        advanceUntilIdle()
        assertTrue(repo.exercises().first().isEmpty())
    }

    @Test
    fun `undo delete restores the exercise`() = runTest {
        repo.upsertAll(listOf(Exercise("my-1", "Custom", ExerciseType.WEIGHT_REPS, isCustom = true)))
        viewModel = ExerciseCatalogViewModel(repo)
        subscribe(viewModel.exercises)
        advanceUntilIdle()
        val exercise = repo.exercises().first().first()

        viewModel.deletePending(exercise)
        advanceUntilIdle()
        assertTrue(viewModel.exercises.value.isEmpty())

        viewModel.undoDelete(exercise)
        advanceUntilIdle()
        assertEquals(1, viewModel.exercises.value.size)
        assertFalse(repo.exercises().first().isEmpty())
    }
}
