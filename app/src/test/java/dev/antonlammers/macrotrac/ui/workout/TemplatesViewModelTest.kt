package dev.antonlammers.macrotrac.ui.workout

import dev.antonlammers.macrotrac.domain.model.SessionExercise
import dev.antonlammers.macrotrac.domain.model.SetEntry
import dev.antonlammers.macrotrac.domain.model.TemplateExercise
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeWorkoutTemplateRepository
    private lateinit var sessions: FakeWorkoutSessionRepository
    private lateinit var viewModel: TemplatesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeWorkoutTemplateRepository()
        sessions = FakeWorkoutSessionRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.subscribe(flow: StateFlow<T>) {
        backgroundScope.launch { flow.collect {} }
    }

    private suspend fun seed(name: String) = repo.save(
        WorkoutTemplate(
            stableId = "s-$name",
            name = name,
            exercises = listOf(TemplateExercise("ex-1", 0, 3)),
        ),
    )

    @Test
    fun `templates are exposed in manual (creation) order`() = runTest {
        seed("Push Day")
        seed("Pull Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()
        assertEquals(listOf("Push Day", "Pull Day"), viewModel.templates.value.map { it.template.name })
    }

    @Test
    fun `moveTemplate reorders and persists the new manual order`() = runTest {
        seed("Push Day")
        seed("Pull Day")
        seed("Legs")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()

        // Mirrors DragReorderColumn: called once per adjacent step while a row is dragged, so moving
        // the first item to the end is two adjacent swaps, not a single jump.
        viewModel.moveTemplate(0, 1)
        advanceUntilIdle()
        viewModel.moveTemplate(1, 2)
        advanceUntilIdle()

        assertEquals(listOf("Pull Day", "Legs", "Push Day"), viewModel.templates.value.map { it.template.name })
        assertEquals(listOf("Pull Day", "Legs", "Push Day"), repo.templates().first().map { it.name })
    }

    @Test
    fun `last used date is the most recent session started from that template, active sessions count`() = runTest {
        val pushId = seed("Push Day")
        seed("Pull Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()

        val pushTemplate = repo.template(pushId).first()!!
        sessions.save(
            WorkoutSession(
                stableId = "old", date = LocalDate.of(2026, 7, 1), isActive = false,
                startedAtMs = 1, endedAtMs = 2, templateStableId = pushTemplate.stableId,
            ),
        )
        sessions.save(
            WorkoutSession(
                stableId = "new", date = LocalDate.of(2026, 7, 10), isActive = true,
                startedAtMs = 3, templateStableId = pushTemplate.stableId,
            ),
        )
        advanceUntilIdle()

        val items = viewModel.templates.value
        assertEquals(LocalDate.of(2026, 7, 10), items.first { it.template.name == "Push Day" }.lastUsedDate)
        assertNull(items.first { it.template.name == "Pull Day" }.lastUsedDate)
    }

    @Test
    fun `a free workout (no template) does not affect any template's last used date`() = runTest {
        seed("Push Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()

        sessions.save(
            WorkoutSession(
                stableId = "free", date = LocalDate.of(2026, 7, 10), isActive = false,
                startedAtMs = 1, endedAtMs = 2,
                exercises = listOf(SessionExercise(exerciseStableId = "squat", position = 0, sets = listOf(SetEntry(position = 0, weightKg = 1.0, reps = 1)))),
            ),
        )
        advanceUntilIdle()

        assertNull(viewModel.templates.value.single().lastUsedDate)
    }

    @Test
    fun `deferred delete hides then removes the template`() = runTest {
        val id = seed("Push Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()
        val template = viewModel.templates.value.first { it.template.id == id }.template

        assertEquals(1, viewModel.templates.value.size)

        viewModel.deletePending(template)
        advanceUntilIdle()
        assertTrue(viewModel.templates.value.isEmpty()) // hidden while snackbar shows

        viewModel.confirmDelete(template)
        advanceUntilIdle()
        assertTrue(repo.templates().first().isEmpty())
    }

    @Test
    fun `undo delete restores the template`() = runTest {
        val id = seed("Push Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()
        val template = viewModel.templates.value.first { it.template.id == id }.template

        viewModel.deletePending(template)
        advanceUntilIdle()
        assertTrue(viewModel.templates.value.isEmpty())

        viewModel.undoDelete(template)
        advanceUntilIdle()
        assertEquals(1, viewModel.templates.value.size)
        assertEquals(1, repo.templates().first().size)
    }
}
