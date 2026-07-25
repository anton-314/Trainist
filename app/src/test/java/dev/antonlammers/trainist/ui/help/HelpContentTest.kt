package dev.antonlammers.trainist.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Help Center is ~70 hand-written string ids wired into pairs, and a copy-paste slip there is
 * invisible until someone scrolls to that row on a device. These checks catch the realistic
 * mistakes — a repeated question, an answer pointing back at its own heading, an empty section.
 */
class HelpContentTest {

    private val allEntries = HelpContent.gettingStarted + HelpContent.sections.flatMap { it.entries }

    @Test
    fun `every section has entries`() {
        assertTrue(HelpContent.gettingStarted.isNotEmpty())
        HelpContent.sections.forEach { section ->
            assertTrue("section ${section.title} is empty", section.entries.isNotEmpty())
        }
    }

    @Test
    fun `no entry is listed twice`() {
        val titles = allEntries.map { it.title }
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun `no body text is reused across entries`() {
        val bodies = allEntries.map { it.body }
        assertEquals(bodies.size, bodies.distinct().size)
    }

    @Test
    fun `title and body of an entry are different strings`() {
        allEntries.forEach { entry ->
            assertTrue("entry ${entry.title} points at itself", entry.title != entry.body)
        }
    }

    @Test
    fun `section headings are distinct`() {
        val titles = HelpContent.sections.map { it.title }
        assertEquals(titles.size, titles.distinct().size)
    }
}
