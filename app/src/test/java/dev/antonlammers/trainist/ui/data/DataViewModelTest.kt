package dev.antonlammers.trainist.ui.data

import dev.antonlammers.trainist.domain.backup.BackupImporter
import dev.antonlammers.trainist.fake.FakeBackupExporter
import dev.antonlammers.trainist.fake.FakeBackupImporter
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
class DataViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val exporter = FakeBackupExporter()
    private val importer = FakeBackupImporter()

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(settings: FakeSettingsRepository = FakeSettingsRepository()) =
        DataViewModel(exporter, importer, settings)

    // --- Reminder toggle -------------------------------------------------------------------------

    @Test
    fun `init reads the persisted reminder flag`() = runTest {
        val viewModel = viewModel(FakeSettingsRepository(reminderEnabled = false))

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.reminderEnabled)
    }

    @Test
    fun `toggling the reminder updates state and persists it`() = runTest {
        val settings = FakeSettingsRepository(reminderEnabled = true)
        val viewModel = viewModel(settings)
        advanceUntilIdle()

        viewModel.setReminderEnabled(false)

        // State flips synchronously so the switch never lags behind the tap.
        assertFalse(viewModel.uiState.value.reminderEnabled)
        advanceUntilIdle()
        assertFalse(settings.isReminderEnabled())
    }

    // --- Export ----------------------------------------------------------------------------------

    @Test
    fun `successful export hands the uri to the caller and clears loading`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        var shared: String? = null

        viewModel.export { shared = it }
        advanceUntilIdle()

        assertEquals("content://dev.antonlammers.trainist.fileprovider/cache/backup.zip", shared)
        assertEquals(1, exporter.exportCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `export reports loading while it runs`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        var loadingDuringExport = false
        exporter.whileRunning = { loadingDuringExport = viewModel.uiState.value.isLoading }

        viewModel.export {}
        advanceUntilIdle()

        assertTrue(loadingDuringExport)
    }

    @Test
    fun `failed export surfaces the error, skips the callback and clears loading`() = runTest {
        exporter.failure = IllegalStateException("no space left")
        val viewModel = viewModel()
        advanceUntilIdle()
        var shared: String? = null

        viewModel.export { shared = it }
        advanceUntilIdle()

        assertNull(shared)
        assertEquals(DataMessage.ExportFailed("no space left"), viewModel.uiState.value.message)
        // The buttons re-enable off this flag — a stuck `true` would lock the user out.
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // --- Import ----------------------------------------------------------------------------------

    @Test
    fun `successful import maps every counter onto the result message`() = runTest {
        importer.result = BackupImporter.Result(
            foodImported = 12,
            foodSkipped = 3,
            weightImported = 7,
            goalRestored = true,
            customFoodsImported = 4,
            exercisesImported = 5,
            templatesImported = 2,
            sessionsImported = 9,
            measurementsImported = 6,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.import("content://downloads/backup.zip")
        advanceUntilIdle()

        assertEquals(listOf("content://downloads/backup.zip"), importer.importedUris)
        assertEquals(
            DataMessage.ImportResult(
                foodImported = 12,
                foodSkipped = 3,
                weightImported = 7,
                goalRestored = true,
                customFoodsImported = 4,
                exercisesImported = 5,
                templatesImported = 2,
                sessionsImported = 9,
                measurementsImported = 6,
            ),
            viewModel.uiState.value.message,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful import invokes the onSuccess callback`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        var advanced = false

        viewModel.import("content://downloads/backup.zip") { advanced = true }
        advanceUntilIdle()

        assertTrue(advanced)
    }

    @Test
    fun `failed import surfaces the error and never advances onboarding`() = runTest {
        importer.failure = IllegalArgumentException("corrupt archive")
        val viewModel = viewModel()
        advanceUntilIdle()
        var advanced = false

        viewModel.import("content://downloads/broken.zip") { advanced = true }
        advanceUntilIdle()

        // The onboarding quick-start hangs off this callback: a failed restore must keep the user
        // on the welcome screen rather than dropping them into an empty app.
        assertFalse(advanced)
        assertEquals(DataMessage.ImportFailed("corrupt archive"), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `import reports loading while it runs`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        var loadingDuringImport = false
        importer.whileRunning = { loadingDuringImport = viewModel.uiState.value.isLoading }

        viewModel.import("content://downloads/backup.zip")
        advanceUntilIdle()

        assertTrue(loadingDuringImport)
    }

    // --- Message lifecycle -----------------------------------------------------------------------

    @Test
    fun `clearMessage drops the message so a snackbar shows only once`() = runTest {
        exporter.failure = RuntimeException("boom")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.export {}
        advanceUntilIdle()

        viewModel.clearMessage()

        assertNull(viewModel.uiState.value.message)
    }
}
