package dev.antonlammers.trainist.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
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

    override suspend fun getAppLanguage(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales.toLanguageTags()
    }

    override suspend fun setAppLanguage(tag: String?) {
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private companion object {
        const val PREFS_NAME = "trainist_settings"
        const val KEY_REMINDER_ENABLED = "meal_reminder_enabled"
        const val KEY_STATS_CARD_ORDER = "stats_card_order"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
