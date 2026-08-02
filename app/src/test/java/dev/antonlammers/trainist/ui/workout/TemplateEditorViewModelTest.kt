package dev.antonlammers.trainist.ui.workout

import androidx.lifecycle.SavedStateHandle
import dev.antonlammers.trainist.domain.SupersetPlacement
import dev.antonlammers.trainist.domain.model.Exercise
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.SetType
import dev.antonlammers.trainist.domain.model.TemplateExercise
import dev.antonlammers.trainist.domain.model.WorkoutTemplate
import dev.antonlammers.trainist.fake.FakeExerciseCatalogRepository
import dev.antonlammers.trainist.fake.FakeWorkoutTemplateRepository
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

    private fun normalSets(count: Int) = List(count) { SetType.NORMAL }

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

    // --- add / remove exercise ---

    @Test
    fun `addExercise appends with the default set count, all NORMAL`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("squat", "Squat"))
        assertEquals(listOf("Bench Press", "Squat"), vm.uiState.value.slots.map { it.exerciseName })
        assertTrue(vm.uiState.value.slots.all { it.setTypes == normalSets(3) })
    }

    @Test
    fun `removeExercise drops the slot at the index`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("squat", "Squat"))
        vm.removeExercise(0)
        assertEquals(listOf("Squat"), vm.uiState.value.slots.map { it.exerciseName })
    }

    // --- per-set planning ---

    @Test
    fun `addSet appends a NORMAL set up to the max, removeSet keeps at least one`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))

        vm.addSet(0)
        assertEquals(normalSets(4), vm.uiState.value.slots[0].setTypes)

        repeat(20) { vm.addSet(0) } // clamps at MAX_TARGET_SETS = 20
        assertEquals(20, vm.uiState.value.slots[0].setTypes.size)

        repeat(25) { vm.removeSet(0, 0) } // clamps at MIN_TARGET_SETS = 1
        assertEquals(1, vm.uiState.value.slots[0].setTypes.size)
    }

    @Test
    fun `removeSet drops the set at the given index`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press")) // 3 NORMAL sets
        vm.setSetType(0, 0, SetType.WARMUP)

        vm.removeSet(0, 0) // drop the warmup
        assertEquals(normalSets(2), vm.uiState.value.slots[0].setTypes)
    }

    @Test
    fun `setSetType changes only the targeted set`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press")) // 3 NORMAL sets

        vm.setSetType(0, 0, SetType.WARMUP)
        vm.setSetType(0, 2, SetType.DROP)

        assertEquals(
            listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROP),
            vm.uiState.value.slots[0].setTypes,
        )
    }

    @Test
    fun `addSet, removeSet and setSetType out of range are no-ops`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("bench", "Bench Press"))

        vm.addSet(5)
        vm.removeSet(5, 0)
        vm.setSetType(5, 0, SetType.WARMUP)
        vm.setSetType(0, 99, SetType.WARMUP)

        assertEquals(normalSets(3), vm.uiState.value.slots[0].setTypes)
    }

    // --- reordering ---

    @Test
    fun `moveSlot reorders slots`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("a", "A"))
        vm.addExercise(exercise("b", "B"))
        vm.addExercise(exercise("c", "C"))

        vm.moveSlot(0, 1)
        assertEquals(listOf("B", "A", "C"), vm.uiState.value.slots.map { it.exerciseName })

        vm.moveSlot(2, 1)
        assertEquals(listOf("B", "C", "A"), vm.uiState.value.slots.map { it.exerciseName })
    }

    @Test
    fun `moveSlot out of range is a no-op`() = runTest {
        val vm = editorFor(0)
        vm.addExercise(exercise("a", "A"))
        vm.addExercise(exercise("b", "B"))

        vm.moveSlot(0, -1)
        vm.moveSlot(1, 2)
        assertEquals(listOf("A", "B"), vm.uiState.value.slots.map { it.exerciseName })
    }

    // --- save ---

    @Test
    fun `save persists the template with positions from list order and each slot's planned set types`() = runTest {
        val vm = editorFor(0)
        subscribe(vm.saved)
        vm.onNameChange("  Push Day  ")
        vm.addExercise(exercise("bench", "Bench Press"))
        vm.addExercise(exercise("fly", "Cable Fly"))
        vm.addSet(1)
        vm.setSetType(1, 0, SetType.WARMUP)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.saved.value)
        val saved = templates.templates().first().single()
        assertEquals("Push Day", saved.name)
        assertEquals(
            listOf(
                TemplateExercise("bench", 0, normalSets(3)),
                TemplateExercise("fly", 1, listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.NORMAL)),
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
    fun `existing template loads name and slots with resolved names and set types in order`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press"), exercise("squat", "Squat")))
        val id = templates.save(
            WorkoutTemplate(
                stableId = "tpl-1",
                name = "Push + Leg",
                exercises = listOf(
                    TemplateExercise("bench", 0, listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL)),
                    TemplateExercise("squat", 1, normalSets(5)),
                ),
            ),
        )
        val vm = editorFor(id)
        advanceUntilIdle()

        assertEquals("Push + Leg", vm.uiState.value.name)
        assertEquals(
            listOf("Bench Press" to listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL), "Squat" to normalSets(5)),
            vm.uiState.value.slots.map { it.exerciseName to it.setTypes },
        )
    }

    @Test
    fun `editing an existing template preserves its stable id`() = runTest {
        catalog.upsertAll(listOf(exercise("bench", "Bench Press")))
        val id = templates.save(
            WorkoutTemplate(
                stableId = "tpl-keep",
                name = "Old",
                exercises = listOf(TemplateExercise("bench", 0, normalSets(3))),
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

    // --- planned supersets ---

    /** An editor holding [names] as separate, ungrouped slots. */
    private suspend fun editorWithSlots(scope: TestScope, vararg names: String): TemplateEditorViewModel {
        val vm = editorFor(0)
        scope.subscribe(vm.uiState)
        scope.advanceUntilIdle()
        names.forEach { vm.addExercise(exercise(it, it)) }
        scope.advanceUntilIdle()
        return vm
    }

    @Test
    fun `grouping two slots plans them as one superset`() = runTest {
        val vm = editorWithSlots(this, "bench", "row")

        vm.groupWithNext(0)
        advanceUntilIdle()

        assertEquals(
            mapOf(0 to SupersetPlacement(0, 1, 2), 1 to SupersetPlacement(0, 2, 2)),
            vm.uiState.value.supersets,
        )
    }

    @Test
    fun `a planned superset is persisted with the template`() = runTest {
        val vm = editorWithSlots(this, "bench", "row", "curl")
        vm.groupWithNext(0)
        advanceUntilIdle()

        vm.onNameChange("Push")
        vm.save()
        advanceUntilIdle()

        val saved = templates.templates().first().single()
        assertEquals(listOf(1, 1, null), saved.exercises.map { it.supersetGroupId })
    }

    @Test
    fun `ungrouping a planned superset clears it for both slots`() = runTest {
        val vm = editorWithSlots(this, "bench", "row")
        vm.groupWithNext(0)
        advanceUntilIdle()

        vm.ungroup(1)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.supersets.isEmpty())
    }

    @Test
    fun `removing one slot of a planned superset dissolves it`() = runTest {
        val vm = editorWithSlots(this, "bench", "row")
        vm.groupWithNext(0)
        advanceUntilIdle()

        vm.removeExercise(0)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.supersets.isEmpty())
        assertEquals(listOf<Int?>(null), vm.uiState.value.slots.map { it.supersetGroupId })
    }

    @Test
    fun `dragging a third slot between two grouped ones breaks the superset apart`() = runTest {
        val vm = editorWithSlots(this, "bench", "row", "curl")
        vm.groupWithNext(0) // bench + row
        advanceUntilIdle()

        // Move "curl" from the end into the middle — the group's members are no longer adjacent.
        vm.moveSlot(2, 1)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.supersets.isEmpty())
    }

    @Test
    fun `a saved planned superset is read back when the template is reopened`() = runTest {
        val id = templates.save(
            WorkoutTemplate(
                stableId = "tpl",
                name = "Push",
                exercises = listOf(
                    TemplateExercise("bench", 0, normalSets(3), supersetGroupId = 1),
                    TemplateExercise("row", 1, normalSets(3), supersetGroupId = 1),
                ),
            ),
        )
        val vm = editorFor(id)
        subscribe(vm.uiState)
        advanceUntilIdle()

        assertEquals(
            mapOf(0 to SupersetPlacement(0, 1, 2), 1 to SupersetPlacement(0, 2, 2)),
            vm.uiState.value.supersets,
        )
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
