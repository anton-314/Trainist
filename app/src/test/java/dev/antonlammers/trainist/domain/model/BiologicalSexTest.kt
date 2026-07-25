package dev.antonlammers.trainist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiologicalSexTest {

    @Test
    fun `parse reads valid names case-insensitively`() {
        assertEquals(BiologicalSex.MALE, BiologicalSex.parse("MALE"))
        assertEquals(BiologicalSex.MALE, BiologicalSex.parse("male"))
        assertEquals(BiologicalSex.FEMALE, BiologicalSex.parse("Female"))
    }

    @Test
    fun `parse returns null for unknown, blank or missing input`() {
        // Unlike FoodTag.parse's non-null default, an unparseable profile field means "no profile".
        assertNull(BiologicalSex.parse("OTHER"))
        assertNull(BiologicalSex.parse(""))
        assertNull(BiologicalSex.parse(null))
    }
}
