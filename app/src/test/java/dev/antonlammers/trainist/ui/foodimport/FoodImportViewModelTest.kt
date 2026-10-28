package dev.antonlammers.trainist.ui.foodimport

import dev.antonlammers.trainist.domain.backup.FoodEntryFile
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.fake.FakeFoodEntryFileReader
import dev.antonlammers.trainist.fake.FakeFoodEntryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FoodImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reader = FakeFoodEntryFileReader()
    private val repository = FakeFoodEntryRepository()
    private val date = LocalDate.of(2026, 10, 25)

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FoodImportViewModel(reader, repository)

    private fun entry(name: String) = FoodEntry(
        foodName = name, brand = null, amountGrams = 100.0, kcal = 165.0, proteinG = 31.0,
        carbsG = 0.0, fatG = 3.6, mealCategory = MealCategory.DINNER, date = date, timestampMs = 1L,
    )

    @Test
    fun `starts idle`() {
        assertEquals(FoodImportPhase.Idle, viewModel().uiState.value.phase)
    }

    @Test
    fun `opening a food CSV shows a preview and saves nothing yet`() = runTest {
        val entries = listOf(entry("Hähnchen"), entry("Reis"))
        reader.file = FoodEntryFile.Entries(entries, skipped = 1)
        val viewModel = viewModel()

        viewModel.open("content://downloads/1")
        assertEquals(FoodImportPhase.Loading, viewModel.uiState.value.phase)
        advanceUntilIdle()

        assertEquals(listOf("content://downloads/1"), reader.readUris)
        assertEquals(FoodImportPhase.Preview(entries, skipped = 1), viewModel.uiState.value.phase)
        assertTrue(repository.allEntries().isEmpty())
    }

    @Test
    fun `each unusable file maps to its own error`() = runTest {
        val cases = mapOf(
            FoodEntryFile.Unreadable to FoodImportError.UNREADABLE,
            FoodEntryFile.NotFoodEntries to FoodImportError.NOT_FOOD_ENTRIES,
            FoodEntryFile.NoValidRows to FoodImportError.NO_VALID_ROWS,
        )
        cases.forEach { (file, error) ->
            reader.file = file
            val viewModel = viewModel()

            viewModel.open("content://x")
            advanceUntilIdle()

            assertEquals(FoodImportPhase.Failed(error), viewModel.uiState.value.phase)
        }
    }

    @Test
    fun `confirming adds every previewed entry and reports the count`() = runTest {
        repository.add(entry("Already there"))
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen"), entry("Reis")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()

        viewModel.confirm()
        assertEquals(FoodImportPhase.Idle, viewModel.uiState.value.phase)
        advanceUntilIdle()

        // Added next to the existing entry — an import never replaces anything.
        assertEquals(
            listOf("Already there", "Hähnchen", "Reis"),
            repository.allEntries().map { it.foodName },
        )
        assertEquals(2, viewModel.uiState.value.importedCount)
    }

    @Test
    fun `a second confirm tap does not import the file twice`() = runTest {
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()

        viewModel.confirm()
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(1, repository.allEntries().size)
    }

    @Test
    fun `opening the same file again adds only what is not stored yet`() = runTest {
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen"), entry("Reis")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()
        viewModel.confirm()
        advanceUntilIdle()

        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen"), entry("Reis"), entry("Brokkoli")), skipped = 0)
        viewModel.open("content://x")
        advanceUntilIdle()
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf("Hähnchen", "Reis", "Brokkoli"), repository.allEntries().map { it.foodName })
        assertEquals(1, viewModel.uiState.value.importedCount)
    }

    @Test
    fun `confirm without a preview does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(repository.allEntries().isEmpty())
        assertNull(viewModel.uiState.value.importedCount)
    }

    @Test
    fun `dismissing a preview imports nothing`() = runTest {
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()

        viewModel.dismiss()
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(FoodImportPhase.Idle, viewModel.uiState.value.phase)
        assertTrue(repository.allEntries().isEmpty())
    }

    @Test
    fun `a failing save surfaces as an error instead of a success count`() = runTest {
        repository.addFailure = IllegalStateException("disk full")
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()

        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(FoodImportPhase.Failed(FoodImportError.SAVE_FAILED), viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.importedCount)
    }

    @Test
    fun `the reported count is cleared once shown`() = runTest {
        reader.file = FoodEntryFile.Entries(listOf(entry("Hähnchen")), skipped = 0)
        val viewModel = viewModel()
        viewModel.open("content://x")
        advanceUntilIdle()
        viewModel.confirm()
        advanceUntilIdle()

        viewModel.clearImportedCount()

        assertNull(viewModel.uiState.value.importedCount)
    }
}
