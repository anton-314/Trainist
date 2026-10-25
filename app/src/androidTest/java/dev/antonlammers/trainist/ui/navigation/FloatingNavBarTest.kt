package dev.antonlammers.trainist.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers what the floating pill does only inside a real composition: whether the plus button is
 * actually on screen for a tab, and that the pill's two stacked tab layers (inactive + the focused
 * look shown through the sliding focus) still read as one set of tabs to touch and accessibility.
 */
class FloatingNavBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf(
        NavTab(Screen.Overview, "Ernährung", Icons.Rounded.Restaurant, Icons.Outlined.Restaurant, hasPrimaryAction = true),
        NavTab(Screen.Workout, "Training", Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter, hasPrimaryAction = true),
        NavTab(Screen.Stats, "Statistik", Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    )

    private var addClicks = 0
    private val addFood = PrimaryAction("Mahlzeit hinzufügen", tutorialTarget = null) { addClicks++ }

    @Test
    fun the_plus_button_shows_on_a_tab_that_has_one_and_runs_its_action() {
        compose.setContent {
            FloatingNavBar(tabs, Screen.Overview.route, addFood, onSelect = {})
        }

        compose.onNodeWithContentDescription(addFood.contentDescription).assertIsDisplayed().performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun switching_to_a_tab_without_one_hides_the_plus_even_while_the_old_action_is_still_registered() {
        // The outgoing tab stays composed — and registered — for the whole cross-fade; the button
        // must follow the tapped tab regardless.
        var route by mutableStateOf(Screen.Overview.route)
        compose.setContent {
            FloatingNavBar(tabs, route, primaryAction = addFood, onSelect = {})
        }

        route = Screen.Stats.route
        compose.waitForIdle()

        compose.onNodeWithContentDescription(addFood.contentDescription).assertIsNotDisplayed()
    }

    @Test
    fun tapping_a_tab_selects_it() {
        var selected: Screen? = null
        compose.setContent {
            FloatingNavBar(tabs, Screen.Overview.route, addFood, onSelect = { selected = it })
        }

        compose.onNodeWithContentDescription("Statistik").performClick()

        assertEquals(Screen.Stats, selected)
    }

    @Test
    fun the_decorative_focused_layer_adds_no_second_set_of_tabs() {
        compose.setContent {
            FloatingNavBar(tabs, Screen.Overview.route, addFood, onSelect = {})
        }

        // The active tab is named by its label, the inactive ones by their icon — once each.
        compose.onAllNodesWithText("Ernährung").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Training").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Statistik").assertCountEquals(1)
    }
}
