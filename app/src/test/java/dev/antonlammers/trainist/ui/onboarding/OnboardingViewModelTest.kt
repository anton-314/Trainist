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
    fun `complete persists the flag and completes immediately`() = runTest {
        val settings = FakeSettingsRepository(onboardingCompleted = false)
        val viewModel = OnboardingViewModel(settings)
        advanceUntilIdle()

        viewModel.complete()

        // State flips synchronously so the UI leaves the flow without waiting on the write.
        assertEquals(OnboardingState.Completed, viewModel.state.value)

        advanceUntilIdle()
        assertTrue(settings.isOnboardingCompleted())
    }
}
