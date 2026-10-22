package dev.antonlammers.trainist.ui.navigation

import dev.antonlammers.trainist.ui.tutorial.TutorialTarget
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PrimaryActionHostTest {

    private val overview = PrimaryAction("add food", TutorialTarget.ADD_FOOD) {}
    private val training = PrimaryAction("start workout", TutorialTarget.START_WORKOUT) {}

    @Test
    fun `the registered action is current until it is cleared`() {
        val host = PrimaryActionHost()
        host.register(overview)
        assertSame(overview, host.current)

        host.clear(overview)
        assertNull(host.current)
    }

    @Test
    fun `an outgoing tab disposed after the incoming one registered does not clear it`() {
        // During a tab switch the new screen may register before the old one is disposed.
        val host = PrimaryActionHost()
        host.register(overview)
        host.register(training)

        host.clear(overview)

        assertSame(training, host.current)
    }
}
