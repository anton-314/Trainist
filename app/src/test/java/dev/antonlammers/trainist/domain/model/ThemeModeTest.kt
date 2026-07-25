package dev.antonlammers.trainist.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `parse reads back every persisted name`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.parse(mode.name))
        }
    }

    @Test
    fun `parse tolerates whitespace and casing`() {
        assertEquals(ThemeMode.DARK, ThemeMode.parse("  dark "))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("Light"))
    }

    @Test
    fun `parse falls back to SYSTEM for missing or unknown values`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("SEPIA"))
    }

    @Test
    fun `SYSTEM follows the device setting`() {
        assertEquals(true, ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = false))
    }

    @Test
    fun `a pinned shade overrides the device setting in both directions`() {
        assertEquals(false, ThemeMode.LIGHT.resolveDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.LIGHT.resolveDark(systemInDarkTheme = false))
        assertEquals(true, ThemeMode.DARK.resolveDark(systemInDarkTheme = false))
        assertEquals(true, ThemeMode.DARK.resolveDark(systemInDarkTheme = true))
    }
}
