package dev.antonlammers.trainist.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.antonlammers.trainist.R
import dev.antonlammers.trainist.domain.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns a running rest for its **entire** duration and fires the "rest over" alert itself, on the
 * second.
 *
 * Why a long-lived foreground service rather than a scheduled alarm: nothing else can promise the
 * alert lands exactly at zero. A countdown living in the UI layer dies with the screen's lifecycle
 * (so it never fires while backgrounded), and an *inexact* alarm — the only kind this app may use,
 * since Play restricts the exact-alarm permissions to alarm-clock/timer/calendar apps — is deferred
 * by Doze by seconds to minutes, which is what made the notification tick into the negative before
 * anything happened. A foreground service is exempt from app-standby freezing, and the partial wake
 * lock held alongside it keeps the CPU from suspending with the screen off, so the countdown below
 * runs to completion while the app is backgrounded or the device is locked. No exact-alarm
 * permission is involved, and the [AlarmManager][android.app.AlarmManager] path is gone entirely.
 *
 * The service is a pure executor: `WorkoutSessionViewModel` stays the source of truth for
 * start/pause/resume/±15 s and mirrors its full timer state here via [sync], so an adjustment always
 * moves the actual alert instant. [stop] tears everything down (skip/finish/discard). Swiping the
 * app out of the recents kills the service with the process — the rest anchor persisted on the
 * session restores the timer on the next launch, but the alert for that rest is forfeited (an
 * accepted, explicitly chosen trade-off).
 *
 * The alert plays [R.raw.rest_over] on [AudioAttributes.USAGE_ALARM] — the alarm stream, so it is
 * audible with the ringer silenced and independent of the media volume — while holding
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, which ducks any music playing instead of stopping it. The
 * vibration runs through [Vibrator] directly (alarm usage), never through a notification channel, so
 * the alert channel can stay silent and nothing ever doubles up.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted the service without our state — nothing to resume.
        val exerciseName = intent?.getStringExtra(EXTRA_EXERCISE_NAME) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val totalSeconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, RestTimer.DEFAULT_REST_SECONDS)
        val endAtMs = intent.getLongExtra(EXTRA_END_AT_MS, 0L)
        val pausedRemainingMs = intent.getLongExtra(EXTRA_PAUSED_REMAINING_MS, NOT_PAUSED)
            .takeIf { it != NOT_PAUSED }
        val timer = RestTimer(totalSeconds, endAtMs, pausedRemainingMs)

        // A new/updated rest silences a still-sounding alert from the previous one.
        stopAlert()
        countdownJob?.cancel()

        val remainingMs = timer.remainingMs(System.currentTimeMillis())
        if (timer.isPaused) {
            releaseWakeLock()
            startForegroundCompat(
                RestTimerNotifier.NOTIFICATION_ID_ONGOING,
                RestTimerNotifier.buildPaused(this, exerciseName, ceilSeconds(remainingMs)),
            )
        } else {
            acquireWakeLock(remainingMs)
            startForegroundCompat(
                RestTimerNotifier.NOTIFICATION_ID_ONGOING,
                RestTimerNotifier.buildOngoing(this, exerciseName, timer.endAtMs),
            )
            countdownJob = scope.launch {
                // Recomputed from the wall clock every pass, so the alert instant is exact even if a
                // delay overshoots; the final sleep is trimmed to the remainder for sub-second accuracy.
                while (true) {
                    val left = timer.remainingMs(System.currentTimeMillis())
                    if (left <= 0L) break
                    delay(left.coerceAtMost(TICK_MS))
                }
                fireAlert()
            }
        }
        return START_NOT_STICKY
    }

    private fun fireAlert() {
        // The alert is a NEW notification on its own id: Android never moves an already-posted
        // notification to another channel, so reusing the silent LOW ongoing id would keep it silent.
        // Promoting it to the foreground notification first keeps the service a legal FGS while it
        // plays; cancelling the countdown afterwards avoids a moment with both on screen.
        startForegroundCompat(RestTimerNotifier.NOTIFICATION_ID_ALERT, RestTimerNotifier.buildExpired(this))
        NotificationManagerCompat.from(this).cancel(RestTimerNotifier.NOTIFICATION_ID_ONGOING)

        vibrate()
        playTone()

        scope.launch {
            delay(ALERT_DURATION_MS)
            stopAlert()
            // DETACH, not REMOVE: the "rest over" notification stays until the user acts on it —
            // re-posted plainly so detaching does not leave it stuck with the FGS FLAG_NO_CLEAR.
            stopForeground(STOP_FOREGROUND_DETACH)
            RestTimerNotifier.repostExpiredDismissible(this@RestTimerService)
            stopSelf()
        }
    }

    private fun playTone() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val attributes = alarmAudioAttributes()
        requestAudioFocus(audioManager, attributes)
        player = MediaPlayer.create(this, R.raw.rest_over, attributes, audioManager.generateAudioSessionId())
            ?.apply {
                setOnCompletionListener { releaseAudioFocus() }
                start()
            }
    }

    private fun requestAudioFocus(audioManager: AudioManager, attributes: AudioAttributes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
                .also { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun releaseAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val request = focusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request != null) {
            audioManager.abandonAudioFocusRequest(request)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator = v
        // Alarm usage on every path, so the buzz survives Do Not Disturb the same way the tone does.
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> v.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION") v.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, -1),
                alarmAudioAttributes(),
            )
            else -> @Suppress("DEPRECATION") v.vibrate(VIBRATION_PATTERN, -1, alarmAudioAttributes())
        }
    }

    private fun alarmAudioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun stopAlert() {
        vibrator?.cancel()
        vibrator = null
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        releaseAudioFocus()
    }

    /**
     * Keeps the CPU awake for the rest of the countdown (plus the alert) — without it the device
     * suspends with the screen off and the countdown below simply stops running.
     */
    private fun acquireWakeLock(remainingMs: Long) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { acquire(remainingMs + ALERT_DURATION_MS + WAKE_LOCK_MARGIN_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE (and the manifest's matching "specialUse" value) only
        // exist from API 34 — passing a type the platform cannot match to the manifest would throw.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun ceilSeconds(ms: Long): Int = ((ms + 999L) / 1000L).toInt()

    override fun onDestroy() {
        countdownJob?.cancel()
        stopAlert()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_EXERCISE_NAME = "exercise_name"
        private const val EXTRA_TOTAL_SECONDS = "total_seconds"
        private const val EXTRA_END_AT_MS = "end_at_ms"
        private const val EXTRA_PAUSED_REMAINING_MS = "paused_remaining_ms"

        /** Sentinel for "running" in [EXTRA_PAUSED_REMAINING_MS] (a paused value is never negative). */
        private const val NOT_PAUSED = -1L

        private const val WAKE_LOCK_TAG = "Trainist:RestTimer"
        private const val WAKE_LOCK_MARGIN_MS = 10_000L
        private const val TICK_MS = 1_000L

        /** A short cue, not a ringing alarm: tone (~0.9 s) + double buzz, then silence. */
        private const val ALERT_DURATION_MS = 2_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)

        /**
         * Mirrors the whole timer state into the service, starting it if needed. Called for every
         * transition (start, resume, pause, ±15 s), so the service never has to reconstruct state.
         */
        fun sync(
            context: Context,
            exerciseName: String,
            totalSeconds: Int,
            endAtMs: Long,
            pausedRemainingMs: Long?,
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                putExtra(EXTRA_END_AT_MS, endAtMs)
                putExtra(EXTRA_PAUSED_REMAINING_MS, pausedRemainingMs ?: NOT_PAUSED)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Ends the rest: stops the service (dismissing its notification) and any lingering alert. */
        fun stop(context: Context) {
            context.stopService(Intent(context, RestTimerService::class.java))
            RestTimerNotifier.cancelAll(context)
        }
    }
}
