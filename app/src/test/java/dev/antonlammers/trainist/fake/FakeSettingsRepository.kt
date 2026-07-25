package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.model.ThemeMode
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    private var reminderEnabled: Boolean = true,
    private var statsCardOrder: List<StatCardType> = StatCardType.DEFAULT_ORDER,
    private var onboardingCompleted: Boolean = false,
    private var appLanguage: String? = null,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
) : SettingsRepository {

    private val _themeMode = MutableStateFlow(initialThemeMode)
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun isReminderEnabled(): Boolean = reminderEnabled

    override suspend fun setReminderEnabled(enabled: Boolean) {
        reminderEnabled = enabled
    }

    override suspend fun statsCardOrder(): List<StatCardType> = statsCardOrder

    override suspend fun setStatsCardOrder(order: List<StatCardType>) {
        statsCardOrder = order
    }

    override suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboardingCompleted = completed
    }

    override suspend fun getAppLanguage(): String? = appLanguage

    override suspend fun setAppLanguage(tag: String?) {
        appLanguage = tag
    }
}
