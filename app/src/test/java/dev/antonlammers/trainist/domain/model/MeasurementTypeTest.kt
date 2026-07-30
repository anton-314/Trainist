package dev.antonlammers.trainist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementTypeTest {

    @Test
    fun `parse reads valid names case-insensitively`() {
        assertEquals(MeasurementType.WAIST, MeasurementType.parse("WAIST"))
        assertEquals(MeasurementType.WAIST, MeasurementType.parse("waist"))
        assertEquals(MeasurementType.BICEPS, MeasurementType.parse("Biceps"))
    }

    @Test
    fun `parse returns null for unknown, blank or missing input`() {
        assertNull(MeasurementType.parse("FOREARM"))
        assertNull(MeasurementType.parse(""))
        assertNull(MeasurementType.parse(null))
    }
}
