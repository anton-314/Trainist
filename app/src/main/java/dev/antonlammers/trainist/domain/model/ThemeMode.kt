package dev.antonlammers.trainist.domain.model

/**
 * The app's appearance setting: follow the system, or pin one of the two "Ink & Paper" shades.
 *
 * Persisted (as [name]) via [dev.antonlammers.trainist.domain.repository.SettingsRepository];
 * [parse] reads it back defensively so an unknown/removed value falls back to [SYSTEM] instead of
 * crashing. [resolveDark] is the single place the choice becomes a dark/light decision — keeping it
 * here (pure, Android-free) means the UI layer only supplies "is the *system* in dark mode".
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun resolveDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun parse(raw: String?): ThemeMode =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } } ?: SYSTEM
    }
}
