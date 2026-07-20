package dev.antonlammers.trainist.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.antonlammers.trainist.MainActivity
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.ui.util.currentAppLocale

/**
 * Builds the rest-timer notifications. It only *builds* them — [RestTimerService] posts them, since
 * they double as its foreground-service notification.
 *
 * - [buildOngoing] / [buildPaused]: low-importance, silent, ongoing notification shown for as long as
 *   a rest is running or paused, both using the **custom content view** (`notification_rest_timer.xml`)
 *   that shows the remaining time LARGE — [buildOngoing] via an embedded
 *   [android.widget.Chronometer] counting down to the end instant (Android keeps it live itself, so
 *   no per-second re-posting), [buildPaused] via the frozen mm:ss text.
 * - [buildExpired]: the alerting "rest over" notification. It is posted **silent by channel** — the
 *   service plays the tone on the alarm stream and vibrates directly, so the channel must never add
 *   sound or vibration of its own on top.
 *
 * Tapping any of them opens the app straight into the live workout session.
 */
object RestTimerNotifier {

    /** Set on the [MainActivity] launch intent so it navigates straight to the live session. */
    const val EXTRA_OPEN_WORKOUT_SESSION = "open_workout_session"

    private const val CHANNEL_ONGOING_ID = "rest_timer_ongoing"
    // Bumped from "rest_timer" → "rest_timer_alert" → "..._v2" → "..._v3" → "..._v4": a channel's
    // importance/sound/vibration are immutable once created, so every sound-config change needs a
    // fresh id. v4 is fully silent — RestTimerService now plays the tone and vibration itself on the
    // alarm stream (audible with the ringer off, unlike a channel sound), and a channel sound would
    // only double it. IMPORTANCE_HIGH is kept so the notification still pops as a heads-up.
    private const val CHANNEL_ALERT_ID = "rest_timer_alert_v4"
    // The ongoing/paused countdown and the "rest over" alert use SEPARATE notification ids on purpose:
    // Android will not move an already-posted notification to a different channel on update, so posting
    // the HIGH-channel alert under the ongoing notification's id would leave it stuck on the silent LOW
    // channel. A distinct id makes the alert a fresh notification that actually alerts.
    internal const val NOTIFICATION_ID_ONGOING = 2002
    internal const val NOTIFICATION_ID_ALERT = 2003

    // Notification-shade text colours (the custom RemoteViews text can't rely on ?android:textColorPrimary
    // resolving to the shade's theme across OEMs): near-black in light mode, near-white in dark mode.
    private const val TEXT_COLOR_LIGHT = 0xFF1A1A1A.toInt()
    private const val TEXT_COLOR_DARK = 0xFFECECEC.toInt()

    /** Running countdown: ongoing, silent, big live chronometer counting down to [endAtMs]. */
    fun buildOngoing(context: Context, exerciseName: String, endAtMs: Long): android.app.Notification {
        ensureChannels(context)
        // Chronometer.setBase expects the SystemClock.elapsedRealtime() timebase; endAtMs is wall-clock.
        val chronometerBase = SystemClock.elapsedRealtime() + (endAtMs - System.currentTimeMillis())
        val textColor = notificationTextColor(context)
        val content = RemoteViews(context.packageName, R.layout.notification_rest_timer).apply {
            setTextViewText(
                R.id.rest_title,
                context.getString(R.string.rest_timer_ongoing_title, exerciseName),
            )
            setViewVisibility(R.id.rest_chronometer, View.VISIBLE)
            setViewVisibility(R.id.rest_time_static, View.GONE)
            setChronometer(R.id.rest_chronometer, chronometerBase, null, true)
            setChronometerCountDown(R.id.rest_chronometer, true)
            setTextColor(R.id.rest_title, textColor)
            setTextColor(R.id.rest_chronometer, textColor)
        }
        return ongoingBuilder(context, content).build()
    }

    /** Paused countdown: ongoing, silent, big static remaining time (no chronometer while frozen). */
    fun buildPaused(context: Context, exerciseName: String, remainingSeconds: Int): android.app.Notification {
        ensureChannels(context)
        val textColor = notificationTextColor(context)
        val content = RemoteViews(context.packageName, R.layout.notification_rest_timer).apply {
            setTextViewText(
                R.id.rest_title,
                context.getString(R.string.rest_timer_paused_title, exerciseName),
            )
            setViewVisibility(R.id.rest_chronometer, View.GONE)
            setViewVisibility(R.id.rest_time_static, View.VISIBLE)
            setTextViewText(R.id.rest_time_static, formatMmSs(remainingSeconds))
            setTextColor(R.id.rest_title, textColor)
            setTextColor(R.id.rest_time_static, textColor)
        }
        return ongoingBuilder(context, content).build()
    }

    /** Near-black on the light shade, near-white on the dark shade (readable in both). */
    private fun notificationTextColor(context: Context): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) TEXT_COLOR_DARK else TEXT_COLOR_LIGHT
    }

    private fun ongoingBuilder(context: Context, content: RemoteViews) =
        NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openWorkoutPendingIntent(context))

    /** Rest is over: the alerting heads-up, silent by channel (the service makes the noise itself). */
    fun buildExpired(context: Context): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.rest_timer_expired_title))
            .setContentText(context.getString(R.string.rest_timer_expired_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openWorkoutPendingIntent(context))
            .setAutoCancel(true)
            .build()
    }

    /**
     * Re-posts the alert once its service has detached it, so it becomes swipe-dismissible.
     *
     * A notification handed to `startForeground` gains `FLAG_NO_CLEAR`, and `STOP_FOREGROUND_DETACH`
     * leaves that flag on the posted record — without this re-post the "rest over" notification could
     * only be cleared by tapping through into the app. Re-notifying under the same id replaces the
     * record (and its flags) with this plain, `setAutoCancel` one.
     */
    fun repostExpiredDismissible(context: Context) {
        // On Android 13+ posting silently no-ops without the runtime permission — guard to avoid noise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALERT, buildExpired(context))
    }

    /** Dismisses both rest-timer notifications (the alert one outlives its service via DETACH). */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(NOTIFICATION_ID_ONGOING)
            cancel(NOTIFICATION_ID_ALERT)
        }
    }

    private fun openWorkoutPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_WORKOUT_SESSION, true)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun formatMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%d:%02d".format(currentAppLocale(), safe / 60, safe % 60)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING_ID,
                    context.getString(R.string.rest_timer_channel_ongoing_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.rest_timer_channel_ongoing_description)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERT_ID,
                    context.getString(R.string.rest_timer_channel_alert_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.rest_timer_channel_alert_description)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
    }
}
