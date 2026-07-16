package dev.antonlammers.trainist.notification

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

/**
 * Plays the rest-over alert as real alarm-stream audio + direct vibration, independent of the
 * notification's own sound/vibration — a notification's sound plays on the "notification" audio
 * stream and is silenced by ringer/Do Not Disturb settings, which is exactly what made the alert
 * unreliable before this. [AudioAttributes.USAGE_ALARM] plays on the alarm stream instead (the same
 * one a phone's own alarm clock uses — normally exempt from silent mode, audible over headphones like
 * any other audio) and [Vibrator.vibrate] is called directly rather than via a notification channel.
 *
 * Runs as a short-lived foreground service — started from [RestTimerAlarmReceiver] in response to an
 * exact alarm, one of Android's documented background foreground-service-start exemptions — so
 * playback reliably survives with the screen off/locked and the app process not otherwise running.
 * Stops itself once the sound finishes (or after [MAX_RUNTIME_MS] as a safety net).
 */
class RestTimerAlertService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            RestTimerNotifier.NOTIFICATION_ID_ALERT,
            RestTimerNotifier.buildExpiredNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trainist:RestTimerAlert")
            ?.apply { acquire(MAX_RUNTIME_MS) }

        vibrate()
        playSound()
        return START_NOT_STICKY
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        v?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
    }

    private fun playSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            // No ringtone configured at all (device/user has both alarm and notification sound set to
            // "None") — let the vibration alone run its course instead of cancelling it immediately.
            Handler(Looper.getMainLooper()).postDelayed({ stopAlert() }, VIBRATION_ONLY_DURATION_MS)
            return
        }
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setOnCompletionListener { stopAlert() }
            setOnErrorListener { _, _, _ -> stopAlert(); true }
            try {
                setDataSource(this@RestTimerAlertService, uri)
                prepare()
                start()
            } catch (e: Exception) {
                stopAlert()
            }
        }
    }

    private fun stopAlert() {
        if (stopped) return
        stopped = true
        vibrator?.cancel()
        vibrator = null
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }

    companion object {
        // Safety net in case playback hangs — the alert itself is a short clip (a few seconds).
        private const val MAX_RUNTIME_MS = 15_000L
        // Vibration-only fallback duration (no ringtone configured on the device at all).
        private const val VIBRATION_ONLY_DURATION_MS = 4_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400)
    }
}
