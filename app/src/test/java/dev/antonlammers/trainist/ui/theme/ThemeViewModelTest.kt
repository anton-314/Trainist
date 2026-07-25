package dev.antonlammers.trainist.ui.theme

import dev.antonlammers.trainist.domain.model.ThemeMode
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state is the persisted mode, without waiting for a coroutine`() = runTest {
        val settings = FakeSettingsRepository(initialThemeMode = ThemeMode.DARK)

        val viewModel = ThemeViewModel(settings)

        // Read before advancing on purpose: the first composition must already see the real value,
        // or a pinned shade would flash the other one on every cold start.
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `default is following the system setting`() = runTest {
        val viewModel = ThemeViewModel(FakeSettingsRepository())

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode persists and is observed by every reader`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = ThemeViewModel(settings)
        // A second instance stands in for MainActivity's own (activity-scoped) ViewModel.
        val other = ThemeViewModel(settings)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals(ThemeMode.LIGHT, other.themeMode.value)
        assertEquals(ThemeMode.LIGHT, settings.themeMode.value)
    }

    @Test
    fun `switching back to SYSTEM is persisted like any other mode`() = runTest {
        val settings = FakeSettingsRepository(initialThemeMode = ThemeMode.DARK)
        val viewModel = ThemeViewModel(settings)

        viewModel.setThemeMode(ThemeMode.SYSTEM)
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
    }
}
