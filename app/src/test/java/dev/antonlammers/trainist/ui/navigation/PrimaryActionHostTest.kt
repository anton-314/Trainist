package dev.antonlammers.trainist.ui.navigation

import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PrimaryActionHostTest {

    private val overview = PrimaryAction("add food", TutorialTarget.ADD_FOOD) {}
    private val training = PrimaryAction("start workout", TutorialTarget.START_WORKOUT) {}

    @Test
    fun `the registered action belongs to its route until it is cleared`() {
        val host = PrimaryActionHost()
        host.register(Screen.Overview.route, overview)
        assertSame(overview, host.actionFor(Screen.Overview.route))

        host.clear(Screen.Overview.route, overview)
        assertNull(host.actionFor(Screen.Overview.route))
    }

    @Test
    fun `a tab that registers nothing has no action even while the outgoing tab is still composed`() {
        // Overview → Statistik: the Overview stays composed (and registered) for the whole
        // cross-fade, but the button must follow the route the user picked, immediately.
        val host = PrimaryActionHost()
        host.register(Screen.Overview.route, overview)

        assertNull(host.actionFor(Screen.Stats.route))
    }

    @Test
    fun `each tab sees its own action while both are composed`() {
        val host = PrimaryActionHost()
        host.register(Screen.Overview.route, overview)
        host.register(Screen.Workout.route, training)

        assertSame(overview, host.actionFor(Screen.Overview.route))
        assertSame(training, host.actionFor(Screen.Workout.route))
    }

    @Test
    fun `a stale disposal does not clear a newer registration under the same route`() {
        // Returning to a tab can register its fresh action before the previous one is disposed.
        val host = PrimaryActionHost()
        val newerOverview = PrimaryAction("add food", TutorialTarget.ADD_FOOD) {}
        host.register(Screen.Overview.route, overview)
        host.register(Screen.Overview.route, newerOverview)

        host.clear(Screen.Overview.route, overview)

        assertSame(newerOverview, host.actionFor(Screen.Overview.route))
    }

    @Test
    fun `no route means no action`() {
        val host = PrimaryActionHost()
        host.register(null, overview)

        assertNull(host.actionFor(null))
    }
}
