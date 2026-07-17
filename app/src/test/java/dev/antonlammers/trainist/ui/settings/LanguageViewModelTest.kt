package dev.antonlammers.trainist.ui.settings

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state reflects the persisted language`() = runTest {
        val settings = FakeSettingsRepository(appLanguage = "en")
        val viewModel = LanguageViewModel(settings)

        advanceUntilIdle()

        assertEquals("en", viewModel.language.value)
    }

    @Test
    fun `initial state is null when following the system language`() = runTest {
        val settings = FakeSettingsRepository(appLanguage = null)
        val viewModel = LanguageViewModel(settings)

        advanceUntilIdle()

        assertNull(viewModel.language.value)
    }

    @Test
    fun `setLanguage updates state immediately and persists`() = runTest {
        val settings = FakeSettingsRepository(appLanguage = null)
        val viewModel = LanguageViewModel(settings)
        advanceUntilIdle()

        viewModel.setLanguage("de")

        // State flips synchronously so the UI reflects the pick without waiting on the write.
        assertEquals("de", viewModel.language.value)

        advanceUntilIdle()
        assertEquals("de", settings.getAppLanguage())
    }

    @Test
    fun `setLanguage with null reverts to following the system language`() = runTest {
        val settings = FakeSettingsRepository(appLanguage = "en")
        val viewModel = LanguageViewModel(settings)
        advanceUntilIdle()

        viewModel.setLanguage(null)

        assertNull(viewModel.language.value)
        advanceUntilIdle()
        assertNull(settings.getAppLanguage())
    }
}
