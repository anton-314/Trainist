package dev.antonlammers.trainist.ui.addfood

import dev.antonlammers.trainist.domain.model.Food
import dev.antonlammers.trainist.domain.model.FoodTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFoodFormStateTest {

    @Test
    fun `a new form is empty, invalid and folds the optional nutrients away`() {
        val form = CustomFoodFormState()

        assertEquals("", form.name)
        assertEquals("", form.kcal)
        assertFalse(form.isValid)
        assertFalse(form.optionalExpanded)
        assertNull(form.toFood())
    }

    @Test
    fun `a name typed into the search pre-fills the form`() {
        assertEquals("skyr", CustomFoodFormState(prefillName = "skyr").name)
    }

    @Test
    fun `name, kcal and all three macros are required - the optional nutrients are not`() {
        val form = filled()
        assertTrue(form.isValid)

        form.fat = ""
        assertFalse(form.isValid)

        form.fat = "0"
        form.name = "  "
        assertFalse(form.isValid)
    }

    @Test
    fun `comma and period both parse, and blanks become clean values`() {
        val form = filled().apply {
            name = " Granola "
            brand = "  "
            protein = "11,5"
            salt = "0.2"
            tag = FoodTag.NEUTRAL
        }

        val food = form.toFood()!!

        assertEquals("Granola", food.name)
        assertNull(food.brand)
        assertEquals(11.5, food.proteinPer100g, 0.0)
        assertEquals(0.2, food.saltPer100g, 0.0)
        assertEquals(0.0, food.sugarPer100g, 0.0)
        assertEquals(FoodTag.NEUTRAL, food.tag)
    }

    @Test
    fun `editing shows the food's values without a trailing zero and keeps its id`() {
        val existing = Food(
            id = "7", name = "Pesto Rosso", brand = "Mama",
            kcalPer100g = 390.0, proteinPer100g = 5.0, carbsPer100g = 9.5, fatPer100g = 37.0,
            fiberPer100g = 2.0, tag = FoodTag.UNHEALTHY,
        )

        val form = CustomFoodFormState(initial = existing)

        assertEquals("390", form.kcal)
        assertEquals("9.5", form.carbs)
        assertEquals("", form.sugar)
        assertEquals("2", form.fiber)
        // Something optional to see, so it starts unfolded.
        assertTrue(form.optionalExpanded)
        assertEquals(existing, form.toFood())
    }

    private fun filled() = CustomFoodFormState().apply {
        name = "Granola"
        kcal = "452"
        protein = "11"
        carbs = "58"
        fat = "19"
    }
}
