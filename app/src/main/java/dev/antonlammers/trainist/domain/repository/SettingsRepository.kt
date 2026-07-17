package dev.antonlammers.trainist.domain.repository

import dev.antonlammers.trainist.domain.model.StatCardType

/**
 * Lightweight key/value app settings. Kept Android-free so ViewModels and the reminder worker
 * can depend on the interface and tests can substitute a fake.
 */
interface SettingsRepository {
    /** Whether the daily "you haven't tracked anything yet" reminder is enabled. Default: true. */
    suspend fun isReminderEnabled(): Boolean

    suspend fun setReminderEnabled(enabled: Boolean)

    /** User-chosen Stats-screen card order. Defaults to [StatCardType.DEFAULT_ORDER]. */
    suspend fun statsCardOrder(): List<StatCardType>

    suspend fun setStatsCardOrder(order: List<StatCardType>)

    /**
     * Whether the first-launch welcome/onboarding flow has been completed. Default: false, so a
     * fresh install shows the welcome screen exactly once. Set to true after any onboarding path
     * (backup quick-start, start-empty, or the goals guide) finishes.
     */
    suspend fun isOnboardingCompleted(): Boolean

    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * The user's chosen app language as a BCP-47 tag (e.g. "de", "en"), or `null` to follow the
     * system language. Backed by the Android per-app language mechanism, which already persists
     * this itself — the repository is a thin, testable wrapper around it.
     */
    suspend fun getAppLanguage(): String?

    suspend fun setAppLanguage(tag: String?)
}
