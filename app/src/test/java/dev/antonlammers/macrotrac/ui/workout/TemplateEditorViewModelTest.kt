package dev.antonlammers.macrotrac.ui.workout

import androidx.lifecycle.SavedStateHandle
import dev.antonlammers.macrotrac.domain.model.Exercise
import dev.antonlammers.macrotrac.domain.model.ExerciseType
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.fake.FakeExerciseCatalogRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var templates: FakeWorkoutTemplateRepository
    private lateinit var catalog: FakeExerciseCatalogRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        templates = FakeWorkoutTemplateRepository()
        catalog = FakeExerciseCatalogRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private fun exercise(id: String, name: String) =
        Exercise(stableId = id, name = name, type = ExerciseType.WEIGHT_REPS, isCustom = false)

    private fun editorFor(templateId: Long) =
        TemplateEditorViewModel(templates, catalog, SavedStateHandle(mapOf("templateId" to templateId)))

    // --- validation ---

    @Test
    fun `new template cannot be saved until it has a name and an exercise`() = runTest {
        val vm = editorFor(0)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)

        vm.onNameChange("Push Day")
        assertFalse(vm.uiState.value.canSave) // no exercises yet

        vm.addExercise(exercise("bench", "Bench Press"))
        assertTrue(vm.uiState.value.canSave)

        vm.onNameChange("   ")
        assertFalse(vm.uiState.value.canSave) // blank name
    }

    // --- add / remove ---

    @Test
    fun `addExercise appends with the default target-set count`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("squat", "Squat"))
        assertEquals(listOf("Bench Press", "Squat"), vm.uiState.value.slots.map { it.exerciseName })
        assertTrue(vm.uiState.value.slots.all { it.targetSets == 3 })
    }

    @Test
    fun `removeExercise drops the slot at the index`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("squat", "Squat"))
        vm.removeExercise(0)
        assertEquals(listOf("Squat"), vm.uiState.value.slots.map { it.exerciseName })
    }

    // --- target sets ---

    @Test
    fun `setTargetSets clamps to the allowed range`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))

        vm.setTargetSets(0, 5)
        assertEquals(5, vm.uiState.value.slots[0].targetSets)

        vm.setTargetSets(0, 0)
        assertEquals(1, vm.uiState.value.slots[0].targetSets) // min clamp

        vm.setTargetSets(0, 999)
        assertEquals(20, vm.uiState.value.slots[0].targetSets) // max clamp
    }

    // --- reordering ---

    @Test
    fun `moveUp and moveDown reorder slots`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("a", "A"))
        vm.addExercise(exercise("b", "B"))
        vm.addExercise(exercise("c", "C"))

        vm.moveDown(0)
        assertEquals(listOf("B", "A", "C"), vm.uiState.value.slots.map { it.exerciseName })

        vm.moveUp(2)
        assertEquals(listOf("B", "C", "A"), vm.uiState.value.slots.map { it.exerciseName })
    }

    @Test
    fun `reorder at the edges is a no-op`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("a", "A"))
        vm.addExercise(exercise("b", "B"))

        vm.moveUp(0)
        vm.moveDown(1)
        assertEquals(listOf("A", "B"), vm.uiState.value.slots.map { it.exerciseName })
    }

    // --- save ---

    @Test
    fun `save persists the template with positions from list order`() = runTest {
        val vm = editorFor(0)
        subscribe(vm.saved)
        vm.onNameChange("  Push Day  ")
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("fly", "Cable Fly"))
        vm.setTargetSets(1, 4)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.saved.value)
        val saved = templates.templates().first().single()
        assertEquals("Push Day", saved.name)
        assertEquals(
            listOf(
                TemplateExercise("bench", 0, 3),
                TemplateExercise("fly", 1, 4),
            ),
            saved.exercises,
        )
        assertTrue(saved.stableId.isNotBlank())
    }

    @Test
    fun `save is ignored when the template is invalid`() = runTest {
        val vm = editorFor(0)
        vm.onNameChange("No exercises")
        vm.save()
        advanceUntilIdle()
        assertTrue(templates.templates().first().isEmpty())
        assertFalse(vm.saved.value)
    }

    // --- loading an existing template ---

    @Test
    fun `existing template loads name and slots with resolved names and set counts in order`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat")))
        val id = templates.save(
            WorkoutTemplate(
                stableId = "tpl-1",
                name = "Push + Leg",
                exercises = listOf(
                    TemplateExercise("bench", 0, 3),
                    TemplateExercise("squat", 1, 5),
                ),
            ),
        )
        val vm = editorFor(id)
        advanceUntilIdle()

        assertEquals("Push + Leg", vm.uiState.value.name)
        assertEquals(
            listOf("Bench Press" to 3, "Squat" to 5),
            vm.uiState.value.slots.map { it.exerciseName to it.targetSets },
        )
    }

    @Test
    fun `editing an existing template preserves its stable id`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val id = templates.save(
            WorkoutTemplate(
                stableId = "tpl-keep",
                name = "Old",
                exercises = listOf(TemplateExercise("bench", 0, 3)),
            ),
        )
        val vm = editorFor(id)
        subscribe(vm.saved)
        advanceUntilIdle()
        vm.onNameChange("Renamed")
        vm.save()
        advanceUntilIdle()

        val saved = templates.templates().first().single()
        assertEquals("Renamed", saved.name)
        assertEquals("tpl-keep", saved.stableId)
    }

    // --- picker ---

    @Test
    fun `picker results filter by query case-insensitively`() = runTest {
        catalog.upsertAll(
            listOf(
                exercise("bench", "Bench Press"),
                exercise("squat", "Squat"),
                exercise("row", "Barbell Row"),
            ),
        )
        val vm = editorFor(0)
        subscribe(vm.pickerResults)
        advanceUntilIdle()
        assertEquals(3, vm.pickerResults.value.size)

        vm.onPickerQueryChange("bar")
        advanceUntilIdle()
        assertEquals(listOf("Barbell Row"), vm.pickerResults.value.map { it.name })
    }
}
