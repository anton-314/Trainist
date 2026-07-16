package dev.antonlammers.trainist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when the rest period is over. Triggered by [RestTimerScheduler] via [android.app.AlarmManager]
 * (not WorkManager) so it wakes the device and fires close to on-time even while idle/locked —
 * WorkManager's delayed one-time work has no such guarantee and can be deferred by Doze for minutes,
 * which reads to the user as "no sound went off".
 *
 * Starts [RestTimerAlertService] rather than posting the alert notification directly: an exact alarm
 * firing is one of Android's documented exemptions to the background foreground-service-start
 * restriction, and only a foreground service reliably keeps the process alive long enough to finish
 * playing the alert sound + vibration once onReceive() returns — a plain BroadcastReceiver has no such
 * guarantee and can be killed mid-playback, especially with the screen off/locked.
 */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, RestTimerAlertService::class.java))
    }
}
