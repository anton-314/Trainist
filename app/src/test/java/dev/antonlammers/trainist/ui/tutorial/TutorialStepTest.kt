package dev.antonlammers.trainist.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour is a hand-written list of targets and string ids, and every mistake in it (a step
 * pointing at the wrong element, a text pasted twice, a tab visited in two chunks) only shows up
 * by walking nine steps on a device. These checks catch them at build time.
 */
class TutorialStepTest {

    @Test
    fun `no element is spotlighted twice`() {
        val targets = TutorialStep.entries.map { it.target }
        assertEquals(targets.size, targets.distinct().size)
    }

    @Test
    fun `every step has its own two texts`() {
        val texts = TutorialStep.entries.flatMap { listOf(it.title, it.body) }
        assertEquals(texts.size, texts.distinct().size)
    }

    @Test
    fun `each tab is visited exactly once`() {
        // Steps sharing a route must be adjacent — the tour walks a tab top to bottom instead of
        // bouncing the user back and forth between tabs.
        val routeRuns = TutorialStep.entries.map { it.route }
            .fold(mutableListOf<String>()) { runs, route ->
                if (runs.lastOrNull() != route) runs += route
                runs
            }
        assertEquals(routeRuns.size, routeRuns.distinct().size)
    }

    @Test
    fun `the tour starts on the app's start destination`() {
        // Anything else would make the very first step a tab switch away from where the user lands.
        assertEquals(TutorialStep.first.route, TutorialStep.ADD_FOOD.route)
    }

    @Test
    fun `numbering runs from one to the step count`() {
        assertEquals((1..TutorialStep.count).toList(), TutorialStep.entries.map { it.number })
    }

    @Test
    fun `only the last step is the last one`() {
        assertEquals(1, TutorialStep.entries.count { it.isLast })
        assertTrue(TutorialStep.entries.last().isLast)
    }

    @Test
    fun `stepping runs the list end to end`() {
        assertNull(TutorialStep.first.previous())
        assertNull(TutorialStep.entries.last().next())

        var step: TutorialStep? = TutorialStep.first
        val walked = mutableListOf<TutorialStep>()
        while (step != null) {
            walked += step
            step = step.next()
        }
        assertEquals(TutorialStep.entries.toList(), walked)
    }
}
