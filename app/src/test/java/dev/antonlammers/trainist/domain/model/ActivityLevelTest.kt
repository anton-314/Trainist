package dev.antonlammers.trainist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityLevelTest {

    @Test
    fun `parse reads valid names case-insensitively`() {
        assertEquals(ActivityLevel.SEDENTARY, ActivityLevel.parse("SEDENTARY"))
        assertEquals(ActivityLevel.VERY_ACTIVE, ActivityLevel.parse("very_active"))
    }

    @Test
    fun `parse returns null for unknown, blank or missing input`() {
        assertNull(ActivityLevel.parse("EXTREME"))
        assertNull(ActivityLevel.parse(""))
        assertNull(ActivityLevel.parse(null))
    }
}
