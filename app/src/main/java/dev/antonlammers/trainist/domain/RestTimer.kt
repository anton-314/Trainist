package dev.antonlammers.trainist.domain

/**
 * Pure, Android-free state of a rest timer. It is anchored to a wall-clock end time
 * ([endAtMs]) so the remaining time is always recomputed from "now" — the countdown stays correct
 * across backgrounding, and the background notification is scheduled for the same instant.
 *
 * All transitions are pure functions returning a new state, so the timing/state logic is unit-
 * testable with a fixed clock (like `MealReminderScheduler.initialDelayMillis`). The Android side
 * (WorkManager scheduling, notification) only ever reads [remainingMs].
 *
 * When paused, the frozen remaining time is held in [pausedRemainingMs] and [endAtMs] is ignored.
 */
data class RestTimer(
    val totalSeconds: Int,
    val endAtMs: Long,
    val pausedRemainingMs: Long? = null,
) {
    val isPaused: Boolean get() = pausedRemainingMs != null

    /** Milliseconds left at [nowMs], never negative. Frozen while paused. */
    fun remainingMs(nowMs: Long): Long = (pausedRemainingMs ?: (endAtMs - nowMs)).coerceAtLeast(0L)

    /** True once the running countdown has elapsed (never while paused). */
    fun isFinished(nowMs: Long): Boolean = !isPaused && nowMs >= endAtMs

    /** Freeze the countdown, remembering how much was left. No-op if already paused. */
    fun paused(nowMs: Long): RestTimer =
        if (isPaused) this else copy(pausedRemainingMs = remainingMs(nowMs))

    /** Resume from a paused state, re-anchoring the end time to [nowMs]. No-op if running. */
    fun resumed(nowMs: Long): RestTimer =
        pausedRemainingMs?.let { copy(endAtMs = nowMs + it, pausedRemainingMs = null) } ?: this

    /**
     * Add or subtract [deltaSeconds] (e.g. +15 / −15). [totalSeconds] grows/shrinks with it so the
     * progress fraction stays consistent; the remaining time never drops below zero.
     */
    fun adjusted(nowMs: Long, deltaSeconds: Int): RestTimer {
        val deltaMs = deltaSeconds * 1000L
        val newTotal = (totalSeconds + deltaSeconds).coerceAtLeast(MIN_REST_SECONDS)
        return if (isPaused) {
            copy(totalSeconds = newTotal, pausedRemainingMs = (pausedRemainingMs!! + deltaMs).coerceAtLeast(0L))
        } else {
            copy(totalSeconds = newTotal, endAtMs = (endAtMs + deltaMs).coerceAtLeast(nowMs))
        }
    }

    companion object {
        /** Global default rest duration when an exercise has no per-exercise override. */
        const val DEFAULT_REST_SECONDS = 180

        /** Floor for a configured/adjusted duration (keeps the timer meaningful). */
        const val MIN_REST_SECONDS = 5

        /** Start a fresh, running timer of [seconds] (clamped to at least [MIN_REST_SECONDS]). */
        fun start(nowMs: Long, seconds: Int): RestTimer {
            val clamped = seconds.coerceAtLeast(MIN_REST_SECONDS)
            return RestTimer(totalSeconds = clamped, endAtMs = nowMs + clamped * 1000L)
        }
    }
}
