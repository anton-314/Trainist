package dev.antonlammers.macrotrac.ui.workout

import dev.antonlammers.macrotrac.domain.model.TemplateExercise
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun `templates are exposed sorted by name`() = runTest {
        seed("Pull Day")
        seed("Push Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()
        assertEquals(listOf("Pull Day", "Push Day"), viewModel.templates.value.map { it.name })
    }

    @Test
    fun `deferred delete hides then removes the template`() = runTest {
        val id = seed("Push Day")
        viewModel = TemplatesViewModel(repo, sessions)
        subscribe(viewModel.templates)
        advanceUntilIdle()
        val template = viewModel.templates.value.first { it.id == id }

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
        val template = viewModel.templates.value.first { it.id == id }

        viewModel.deletePending(template)
        advanceUntilIdle()
        assertTrue(viewModel.templates.value.isEmpty())

        viewModel.undoDelete(template)
        advanceUntilIdle()
        assertEquals(1, viewModel.templates.value.size)
        assertEquals(1, repo.templates().first().size)
    }
}
