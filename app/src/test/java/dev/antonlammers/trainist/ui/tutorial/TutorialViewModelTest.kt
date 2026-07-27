package dev.antonlammers.trainist.ui.tutorial

import dev.antonlammers.trainist.fake.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class TutorialViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A tour and the settings it persists into — armed or not, as a fresh launch would find it. */
    private fun tour(pending: Boolean): Pair<FakeSettingsRepository, TutorialViewModel> =
        FakeSettingsRepository(tutorialPending = pending).let { it to TutorialViewModel(it) }

    @Test
    fun `no tour runs unless it was armed`() = runTest {
        val (_, viewModel) = tour(pending =false)

        advanceUntilIdle()

        assertNull(viewModel.step.value)
    }

    @Test
    fun `an armed tour starts at the first step`() = runTest {
        val (_, viewModel) = tour(pending =true)

        advanceUntilIdle()

        assertEquals(TutorialStep.first, viewModel.step.value)
    }

    @Test
    fun `next walks every step in order and then ends the tour`() = runTest {
        val (settings, viewModel) = tour(pending =true)
        advanceUntilIdle()

        val walked = mutableListOf<TutorialStep>()
        repeat(TutorialStep.count) {
            walked += requireNotNull(viewModel.step.value)
            viewModel.next()
        }
        advanceUntilIdle()

        assertEquals(TutorialStep.entries.toList(), walked)
        assertNull(viewModel.step.value)
        // Cleared, so the tour doesn't come back on the next launch.
        assertFalse(settings.isTutorialPending())
    }

    @Test
    fun `skipping ends the tour from any step`() = runTest {
        val (settings, viewModel) = tour(pending =true)
        advanceUntilIdle()
        viewModel.next()
        viewModel.next()

        viewModel.skip()
        advanceUntilIdle()

        assertNull(viewModel.step.value)
        assertFalse(settings.isTutorialPending())
    }

    @Test
    fun `back steps to the previous step`() = runTest {
        val (settings, viewModel) = tour(pending =true)
        advanceUntilIdle()
        viewModel.next()

        viewModel.back()
        advanceUntilIdle()

        assertEquals(TutorialStep.first, viewModel.step.value)
        // Still running — going back is not a way out.
        assertTrue(settings.isTutorialPending())
    }

    @Test
    fun `back on the first step leaves the tour`() = runTest {
        val (settings, viewModel) = tour(pending =true)
        advanceUntilIdle()

        viewModel.back()
        advanceUntilIdle()

        assertNull(viewModel.step.value)
        assertFalse(settings.isTutorialPending())
    }

    @Test
    fun `start re-runs the tour and re-arms the flag`() = runTest {
        val (settings, viewModel) = tour(pending =false)
        advanceUntilIdle()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(TutorialStep.first, viewModel.step.value)
        // Persisted immediately: a process death mid-tour resumes it rather than dropping it.
        assertTrue(settings.isTutorialPending())
    }

    @Test
    fun `advancing while no tour runs does nothing`() = runTest {
        val (_, viewModel) = tour(pending =false)
        advanceUntilIdle()

        viewModel.next()
        advanceUntilIdle()

        assertNull(viewModel.step.value)
    }
}
