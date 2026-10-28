package dev.antonlammers.trainist.ui.addfood

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import dev.antonlammers.trainist.domain.model.MealCategory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The single-food amount sheet follows the tall-sheet shape: it opens fully expanded and its confirm
 * button is pinned below the fields, so it is on screen without any scrolling or dragging. Before,
 * the button sat at the end of non-scrolling content and the keyboard covered it while the amount was
 * typed — only a real composition shows where the button ends up.
 */
class AmountSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val oats = Food(
        id = "", name = "Haferflocken", brand = null,
        kcalPer100g = 370.0, proteinPer100g = 13.5, carbsPer100g = 59.0, fatPer100g = 7.0,
    )

    private fun setContent(amount: String = "100", forIngredient: Boolean = false, onConfirm: () -> Unit = {}) {
        compose.setContent {
            AmountSheet(
                food = oats,
                amount = amount,
                mealCategory = MealCategory.BREAKFAST,
                tag = FoodTag.NONE,
                forIngredient = forIngredient,
                onAmountChange = {},
                onMealCategoryChange = {},
                onTagChange = {},
                onConfirm = onConfirm,
                onDismiss = {},
            )
        }
    }

    @Test
    fun theAddButtonIsOnScreenWithoutScrolling() {
        var confirmed = false
        setContent(onConfirm = { confirmed = true })

        compose.onNodeWithText(string(R.string.addfood_add_button))
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        assertTrue(confirmed)
    }

    @Test
    fun theButtonIsDisabledWithoutAPositiveAmount() {
        setContent(amount = "0")

        compose.onNodeWithText(string(R.string.addfood_add_button)).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun asAnIngredientPickTheCategoryStepsAsideAndTheButtonSaysSo() {
        setContent(forIngredient = true)

        compose.onNodeWithText(string(R.string.addfood_add_to_meal_button)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(string(R.string.addfood_meal_label)).assertDoesNotExist()
    }
}
