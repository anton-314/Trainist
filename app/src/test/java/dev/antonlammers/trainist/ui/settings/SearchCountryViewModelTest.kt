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
class SearchCountryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no stored country means automatic detection`() = runTest {
        val vm = SearchCountryViewModel(FakeSettingsRepository())
        advanceUntilIdle()

        assertNull(vm.country.value)
    }

    @Test
    fun `a stored country is read back on start`() = runTest {
        val vm = SearchCountryViewModel(FakeSettingsRepository(searchCountry = "PT"))
        advanceUntilIdle()

        assertEquals("PT", vm.country.value)
    }

    @Test
    fun `picking a country persists it`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = SearchCountryViewModel(settings)
        advanceUntilIdle()

        vm.setCountry("ES")
        advanceUntilIdle()

        assertEquals("ES", vm.country.value)
        assertEquals("ES", settings.getSearchCountry())
    }

    @Test
    fun `going back to automatic clears the stored country`() = runTest {
        val settings = FakeSettingsRepository(searchCountry = "ES")
        val vm = SearchCountryViewModel(settings)
        advanceUntilIdle()

        vm.setCountry(null)
        advanceUntilIdle()

        assertNull(vm.country.value)
        assertNull(settings.getSearchCountry())
    }

    @Test
    fun `the choice shows immediately rather than waiting for the write`() = runTest {
        // The picker sheet closes on tap; the row underneath must already read the new value.
        val vm = SearchCountryViewModel(FakeSettingsRepository())
        advanceUntilIdle()

        vm.setCountry("IT")

        assertEquals("IT", vm.country.value)
    }
}
