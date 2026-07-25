package dev.antonlammers.trainist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BmrProfileTest {

    private val sex = BiologicalSex.MALE
    private val age = 30
    private val height = 180.0
    private val activity = ActivityLevel.MODERATE

    @Test
    fun `fromParts assembles a profile when all parts are present`() {
        val profile = BmrProfile.fromParts(sex, age, height, activity)
        assertEquals(BmrProfile(sex, age, height, activity), profile)
    }

    @Test
    fun `fromParts returns null when sex is missing`() {
        assertNull(BmrProfile.fromParts(null, age, height, activity))
    }

    @Test
    fun `fromParts returns null when age is missing`() {
        assertNull(BmrProfile.fromParts(sex, null, height, activity))
    }

    @Test
    fun `fromParts returns null when height is missing`() {
        assertNull(BmrProfile.fromParts(sex, age, null, activity))
    }

    @Test
    fun `fromParts returns null when activity level is missing`() {
        assertNull(BmrProfile.fromParts(sex, age, height, null))
    }
}
