package dev.antonlammers.trainist.ui.workout

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WorkoutFormatTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setup() {
        originalLocale = Locale.getDefault()
        // AppCompatDelegate.getApplicationLocales() returns an empty list in a plain JVM test (no
        // Activity has ever initialized its backing store), so currentAppLocale() falls back to
        // Locale.getDefault() — pin it so formatKg()'s decimal separator is deterministic
        // regardless of the machine/CI this test runs on.
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    // --- formatKg ---

    @Test
    fun `formatKg renders whole numbers without a decimal point`() {
        assertEquals("72", formatKg(72.0))
        assertEquals("0", formatKg(0.0))
    }

    @Test
    fun `formatKg rounds to one decimal`() {
        assertEquals("72.5", formatKg(72.5))
        assertEquals("72.6", formatKg(72.55))
        assertEquals("72.5", formatKg(72.549))
    }

    @Test
    fun `formatKg uses the current locale's decimal separator`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("72,5", formatKg(72.5))
    }

    // --- parseWeight ---

    @Test
    fun `parseWeight accepts a period decimal separator`() {
        assertEquals(72.5, parseWeight("72.5"), 0.0)
    }

    @Test
    fun `parseWeight accepts a comma decimal separator`() {
        assertEquals(72.5, parseWeight("72,5"), 0.0)
    }

    @Test
    fun `parseWeight returns zero for blank or invalid input`() {
        assertEquals(0.0, parseWeight(""), 0.0)
        assertEquals(0.0, parseWeight("abc"), 0.0)
    }

    // --- parseReps ---

    @Test
    fun `parseReps strips non-digit characters`() {
        assertEquals(12, parseReps("12"))
        assertEquals(12, parseReps("1a2"))
    }

    @Test
    fun `parseReps returns zero for blank or non-numeric input`() {
        assertEquals(0, parseReps(""))
        assertEquals(0, parseReps("abc"))
    }

    // --- weightToText ---

    @Test
    fun `weightToText is empty for zero`() {
        assertEquals("", weightToText(0.0))
    }

    @Test
    fun `weightToText renders whole numbers without a decimal point`() {
        assertEquals("72", weightToText(72.0))
    }

    @Test
    fun `weightToText renders fractional weights as-is`() {
        assertEquals("72.5", weightToText(72.5))
    }

    // --- repsToText ---

    @Test
    fun `repsToText is empty for zero`() {
        assertEquals("", repsToText(0))
    }

    @Test
    fun `repsToText renders nonzero reps`() {
        assertEquals("12", repsToText(12))
    }
}
