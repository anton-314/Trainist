package dev.antonlammers.trainist.ui.foodimport

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.MealCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The import preview lists however many rows the file holds, so it is a tall sheet: its import
 * button must stay **displayed** however long the list gets — the same off-screen-button trap the
 * Körpermaße sheet fell into, and only observable in a real composition.
 */
class FoodImportSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val importButton get() = compose.onNodeWithText(string(R.string.foodimport_confirm))

    private fun entries(count: Int) = (1..count).map { i ->
        FoodEntry(
            foodName = "Food $i", brand = null, amountGrams = 100.0, kcal = 100.0, proteinG = 10.0,
            carbsG = 10.0, fatG = 1.0, mealCategory = MealCategory.LUNCH,
            date = LocalDate.of(2026, 10, 25), timestampMs = 1L,
        )
    }

    private fun setContent(count: Int): () -> Int {
        var confirmed = 0
        compose.setContent {
            FoodImportSheet(
                preview = FoodImportPhase.Preview(entries(count), skipped = 0),
                onConfirm = { confirmed++ },
                onDismiss = {},
            )
        }
        return { confirmed }
    }

    @Test
    fun theImportButtonIsOnScreenForALongFile() {
        val confirmed = setContent(count = 30)

        importButton.assertIsDisplayed().assertHasClickAction()
        importButton.performClick()
        assertEquals(1, confirmed())
    }

    @Test
    fun theLastEntryIsReachableWithTheButtonStillOnScreen() {
        setContent(count = 30)

        compose.onNodeWithText("Food 30", substring = true).performScrollTo().assertIsDisplayed()
        importButton.assertIsDisplayed()
    }
}
