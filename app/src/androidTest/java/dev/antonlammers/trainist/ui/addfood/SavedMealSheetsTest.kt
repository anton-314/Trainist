package dev.antonlammers.trainist.ui.addfood

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.MealCategory
import dev.antonlammers.trainist.domain.model.SavedMeal
import dev.antonlammers.trainist.domain.model.SavedMealItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The two saved-meal sheets grow with the meal — a long one has more ingredient rows than fit on a
 * screen. Like the body-measurement sheet, the confirm button must then stay **on screen** (only the
 * ingredient list scrolls), which only a real composition can show; a ViewModel test sees the
 * callback fire either way.
 */
class SavedMealSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val ingredientNames = (1..14).map { "Zutat $it" }

    private fun food(name: String) = Food(
        id = "", name = name, brand = null,
        kcalPer100g = 100.0, proteinPer100g = 10.0, carbsPer100g = 5.0, fatPer100g = 1.0,
    )

    @Test
    fun theLogButtonStaysOnScreenForALongMeal() {
        var logged = false
        val meal = SavedMeal(
            stableId = "m",
            name = "Großes Frühstück",
            items = ingredientNames.map { SavedMealItem(food(it), 100.0) },
        )
        compose.setContent {
            MealLogSheet(
                draft = MealLogDraft(meal, meal.items.map { "100" }),
                mealCategory = MealCategory.BREAKFAST,
                onAmountChange = { _, _ -> },
                onMealCategoryChange = {},
                onConfirm = { logged = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText(string(R.string.addfood_add_button))
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        // Every ingredient is reachable by scrolling the list, with the button still in place.
        compose.onNodeWithText(ingredientNames.last()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.addfood_add_button)).assertIsDisplayed().performClick()
        assertTrue(logged)
    }

    @Test
    fun theSaveButtonStaysOnScreenWhileBuildingALongMeal() {
        var saved = false
        val draft = MealDraft(
            origin = MealDraftOrigin.NEW,
            name = "Großes Frühstück",
            items = ingredientNames.mapIndexed { i, name -> MealDraftItem(i.toLong(), food(name), "100") },
        )
        compose.setContent {
            CreateSheet(
                kind = CreateKind.MEAL,
                kindSwitchable = true,
                title = "Neue Mahlzeit",
                onKindChange = {},
                onDismiss = {},
            ) {
                MealEditorForm(
                    draft = draft,
                    onNameChange = {},
                    onAmountChange = { _, _ -> },
                    onRemove = {},
                    onAddIngredient = {},
                    onSave = { saved = true },
                )
            }
        }

        compose.onNodeWithText(string(R.string.common_save)).assertIsDisplayed().assertIsEnabled()
        // "Add ingredient" sits at the end of the list and must be reachable too.
        compose.onNodeWithText(string(R.string.addfood_meal_add_ingredient)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_save)).assertIsDisplayed().performClick()
        assertTrue(saved)
    }

    @Test
    fun theKindSwitchOffersFoodAndMealWhileCreating() {
        val picked = mutableListOf<CreateKind>()
        compose.setContent {
            CreateSheet(
                kind = CreateKind.FOOD,
                kindSwitchable = true,
                title = "Neues Lebensmittel",
                onKindChange = { picked += it },
                onDismiss = {},
            ) {}
        }

        compose.onNodeWithText(string(R.string.addfood_create_kind_meal)).assertIsDisplayed().performClick()
        assertEquals(listOf(CreateKind.MEAL), picked)
    }

    @Test
    fun theKindSwitchIsHiddenWhenEditing() {
        compose.setContent {
            CreateSheet(
                kind = CreateKind.MEAL,
                kindSwitchable = false,
                title = "Mahlzeit bearbeiten",
                onKindChange = {},
                onDismiss = {},
            ) {}
        }

        compose.onNodeWithText("Mahlzeit bearbeiten").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.addfood_create_kind_food)).assertDoesNotExist()
    }

    @Test
    fun theCustomFoodSaveButtonIsDisplayedAndOnlyEnabledOnceTheRequiredFieldsAreFilled() {
        val form = CustomFoodFormState(prefillName = "Granola")
        var saved: Food? = null
        compose.setContent {
            CreateSheet(
                kind = CreateKind.FOOD,
                kindSwitchable = true,
                title = "Neues Lebensmittel",
                onKindChange = {},
                onDismiss = {},
            ) {
                CustomFoodForm(state = form, onSave = { saved = it })
            }
        }

        compose.onNodeWithText(string(R.string.common_save)).assertIsDisplayed().assertIsNotEnabled()

        compose.runOnIdle {
            form.kcal = "452"
            form.protein = "11"
            form.carbs = "58"
            form.fat = "19"
        }

        compose.onNodeWithText(string(R.string.common_save)).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals("Granola", saved?.name)
    }

    @Test
    fun theOptionalNutrientsUnfoldOnTap() {
        compose.setContent {
            CreateSheet(
                kind = CreateKind.FOOD,
                kindSwitchable = false,
                title = "Neues Lebensmittel",
                onKindChange = {},
                onDismiss = {},
            ) {
                CustomFoodForm(state = remember { CustomFoodFormState() }, onSave = {})
            }
        }
        compose.onNodeWithText(string(R.string.addfood_tile_salt)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.addfood_optional_nutrients)).performClick()

        compose.onNodeWithText(string(R.string.addfood_tile_salt)).performScrollTo().assertIsDisplayed()
    }
}
