package dev.antonlammers.trainist.ui.onboarding

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `fresh install shows onboarding`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val viewModel = OnboardingViewModel(settings)

        advanceUntilIdle()

        assertEquals(OnboardingState.Onboarding, viewModel.state.value)
    }

    @Test
    fun `returning user skips straight to the app`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = true)
        val viewModel = OnboardingViewModel(settings)

        advanceUntilIdle()

        assertEquals(OnboardingState.Completed, viewModel.state.value)
    }

    @Test
    fun `complete persists the flag and leaves the flow`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val viewModel = OnboardingViewModel(settings)
        advanceUntilIdle()

        viewModel.complete()
        advanceUntilIdle()

        assertEquals(OnboardingState.Completed, viewModel.state.value)
        assertTrue(settings.isOnboardingCompleted())
    }

    @Test
    fun `the ordinary paths out do not arm the guided tour`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val viewModel = OnboardingViewModel(settings)
        advanceUntilIdle()

        viewModel.complete()
        advanceUntilIdle()

        assertFalse(settings.isTutorialPending())
    }

    @Test
    fun `the new-user path arms the guided tour before it hands over to the app`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val viewModel = OnboardingViewModel(settings)
        advanceUntilIdle()

        viewModel.complete(startTutorial = true)

        // Deliberately still in the flow: the hand-over waits for the writes. Flipping the state is
        // what composes the main navigation, and that is where the tour reads its flag exactly
        // once — flipping ahead of the write would lose the tour.
        assertEquals(OnboardingState.Onboarding, viewModel.state.value)

        advanceUntilIdle()

        assertEquals(OnboardingState.Completed, viewModel.state.value)
        assertTrue(settings.isTutorialPending())
    }
}
