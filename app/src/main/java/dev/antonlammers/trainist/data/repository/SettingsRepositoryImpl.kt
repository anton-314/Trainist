package dev.antonlammers.trainist.data.repository

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.model.ThemeMode
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun isReminderEnabled(): Boolean =
        prefs.getBoolean(KEY_REMINDER_ENABLED, true)

    override suspend fun setReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDER_ENABLED, enabled) }
    }

    override suspend fun statsCardOrder(): List<StatCardType> {
        val raw = prefs.getString(KEY_STATS_CARD_ORDER, null) ?: return StatCardType.DEFAULT_ORDER
        val saved = raw.split(",").mapNotNull { StatCardType.parse(it) }
        // Any card type added after this order was saved (or dropped by the parse above) is
        // appended at the end, so it still shows up instead of silently disappearing.
        val missing = StatCardType.DEFAULT_ORDER.filter { it !in saved }
        return saved + missing
    }

    override suspend fun setStatsCardOrder(order: List<StatCardType>) {
        prefs.edit { putString(KEY_STATS_CARD_ORDER, order.joinToString(",") { it.name }) }
    }

    override suspend fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
    }

    // Per-app language. MainActivity is a plain ComponentActivity (no AppCompatActivity — the
    // Ink & Paper theme forbids a Theme.AppCompat descendant), so no AppCompatDelegate is ever
    // registered. That makes AppCompatDelegate's own storage/apply path a no-op: on API 33+
    // setApplicationLocales() finds no delegate to reach the framework LocaleManager, and on any
    // API level nothing persists the choice. So we drive the platform directly and keep our own
    // persisted copy for the legacy re-apply on startup (applyPersistedAppLanguage).
    override suspend fun getAppLanguage(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The framework LocaleManager is the source of truth on 33+ (it also reflects a change
            // the user made via the system's per-app language setting).
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales
                .toLanguageTags()
                .substringBefore(',')
                .takeIf { it.isNotEmpty() }
        } else {
            prefs.getString(KEY_APP_LANGUAGE, null)
        }

    override suspend fun setAppLanguage(tag: String?) {
        // Persisted for the legacy startup re-apply; harmless (unused) on 33+.
        prefs.edit {
            if (tag == null) remove(KEY_APP_LANGUAGE) else putString(KEY_APP_LANGUAGE, tag)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            // Applies within the running process; MainActivity.recreate() + attachBaseContext then
            // render it. Persistence across restarts is our prefs copy above (AppCompatDelegate's
            // own store is inactive without an AppCompatActivity).
            val locales =
                if (tag == null) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    // Read once here (the binding is a @Singleton) and kept hot, so the theme is known synchronously
    // from the very first composition and a change reaches every observer at once.
    private val _themeMode = MutableStateFlow(ThemeMode.parse(prefs.getString(KEY_THEME_MODE, null)))
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "trainist_settings"
        private const val KEY_REMINDER_ENABLED = "meal_reminder_enabled"
        private const val KEY_STATS_CARD_ORDER = "stats_card_order"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_THEME_MODE = "theme_mode"

        /**
         * Re-applies the persisted per-app language on startup for API < 33, where nothing else
         * survives process death (33+ persists in the framework LocaleManager natively). Call from
         * [dev.antonlammers.trainist.TrainistApp.onCreate] *before* the first Activity is created so
         * `attachBaseContext` picks it up. No-op on 33+ and when following the system language.
         */
        fun applyPersistedAppLanguage(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val tag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, null) ?: return
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}
