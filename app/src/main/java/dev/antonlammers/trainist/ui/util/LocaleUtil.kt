package dev.antonlammers.trainist.ui.util

import androidx.appcompat.app.AppCompatDelegate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's current per-app locale (as set via [AppCompatDelegate.setApplicationLocales]),
 * falling back to the JVM/system default when no per-app override is active. Prefer this over
 * `Locale.getDefault()` directly for anything locale-sensitive (date/number formatting) — the
 * per-app language picker does not change the JVM default below API 33.
 */
fun currentAppLocale(): Locale =
    AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()

/** A [DateTimeFormatter] for [pattern] resolved against [currentAppLocale]. */
fun localizedDateFormatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, currentAppLocale())
