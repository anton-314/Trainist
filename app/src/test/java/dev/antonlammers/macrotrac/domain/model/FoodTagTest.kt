package dev.antonlammers.macrotrac.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodTagTest {

    @Test
    fun `only HEALTHY counts as clean`() {
        assertTrue(FoodTag.HEALTHY.isClean)
        assertFalse(FoodTag.NEUTRAL.isClean)
        assertFalse(FoodTag.UNHEALTHY.isClean)
        assertFalse(FoodTag.NONE.isClean)
    }

    @Test
    fun `selectable excludes NONE and is in display order`() {
        assertEquals(listOf(FoodTag.HEALTHY, FoodTag.NEUTRAL, FoodTag.UNHEALTHY), FoodTag.selectable)
    }

    @Test
    fun `parse reads known names case-insensitively`() {
        assertEquals(FoodTag.HEALTHY, FoodTag.parse("HEALTHY"))
        assertEquals(FoodTag.NEUTRAL, FoodTag.parse("neutral"))
        assertEquals(FoodTag.UNHEALTHY, FoodTag.parse(" Unhealthy "))
    }

    @Test
    fun `parse falls back to NONE for null blank or unknown`() {
        assertEquals(FoodTag.NONE, FoodTag.parse(null))
        assertEquals(FoodTag.NONE, FoodTag.parse(""))
        assertEquals(FoodTag.NONE, FoodTag.parse("bogus"))
    }
}
