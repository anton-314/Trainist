package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.model.StatCardType
import dev.antonlammers.trainist.domain.repository.SettingsRepository

class FakeSettingsRepository(
    private var reminderEnabled: Boolean = true,
    private var statsCardOrder: List<StatCardType> = StatCardType.DEFAULT_ORDER,
    private var onboardingCompleted: Boolean = false,
    private var appLanguage: String? = null,
) : SettingsRepository {

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
