package app.cove.companion.feature.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.cove.companion.R
import app.cove.companion.container
import app.cove.companion.core.Notifications
import app.cove.companion.core.Permissions
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.core.clockText
import app.cove.companion.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the ring screen shows while an alarm is sounding. */
data class RingState(val alarmId: String, val label: String, val minutes: Int, val snoozeMinutes: Int)

/**
 * Foreground (media playback) service that sounds an alarm: system alarm tone looping, optionally
 * rising over [RISE_MS], vibration, and a full-screen-intent notification. Stops itself after [TIMEOUT_MS].
 */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var gentle = true
    private var current: RingState? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> snooze()
            ACTION_STOP -> stopRinging()
            else -> if (intent != null) begin(intent) else stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        silence()
        val ring = RingState(
            intent.getStringExtra(EXTRA_ID).orEmpty(),
            intent.getStringExtra(EXTRA_LABEL).orEmpty(),
            intent.getIntExtra(EXTRA_MINUTES, 0),
            intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 9),
        )
        current = ring
        gentle = intent.getBooleanExtra(EXTRA_GENTLE, true)
        Notifications.createChannels(this)
        ServiceCompat.startForeground(
            this, Notifications.ID_ALARM_RING, notification(ring),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        state.value = ring
        startedAt = System.currentTimeMillis()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cove:alarm").apply { acquire(TIMEOUT_MS + 5_000) }
        runCatching { play(intent.getStringExtra(EXTRA_SOUND).orEmpty()) }.onFailure { CrashHandler.report("alarm-sound", it) }
        runCatching { vibrate() }.onFailure { CrashHandler.report("alarm-vibrate", it) }
        handler.postDelayed(::timeout, TIMEOUT_MS)
        if (!Permissions.notificationsAllowed(this) || !Permissions.fullScreenIntentAllowed(this)) openRingScreen()
    }

    /**
     * With notifications (or full-screen alerts) off the notification cannot raise the ring screen, so open it directly.
     * If Android refuses a background start, MainActivity still shows Stop and Snooze whenever the app is opened.
     */
    private fun openRingScreen() {
        try {
            startActivity(Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            CrashHandler.report("ring-screen", e)
        }
    }

    private fun notification(ring: RingState): android.app.Notification {
        val ringScreen = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(name: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, AlarmRingService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = clockText(ring.minutes)
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ring.label.ifBlank { "Alarm" })
            .setContentText(time.digits + time.suffix)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .addAction(0, "Snooze ${ring.snoozeMinutes} min", action(ACTION_SNOOZE, 1))
            .addAction(0, "Stop", action(ACTION_STOP, 2))
            .build()
    }

    private fun play(sound: String) {
        val type = when (sound) {
            "Chime" -> RingtoneManager.TYPE_NOTIFICATION
            "Ringtone" -> RingtoneManager.TYPE_RINGTONE
            else -> RingtoneManager.TYPE_ALARM
        }
        val uris = listOfNotNull(type, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .distinct()
            .flatMap { listOf(RingtoneManager.getActualDefaultRingtoneUri(this, it), RingtoneManager.getDefaultUri(it)) }
            .filterNotNull()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        getSystemService(AudioManager::class.java)
            .requestAudioFocus(android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build())
        for (uri in uris) {
            val mp = prepare(uri, attrs) ?: continue
            player = mp
            mp.setVolume(if (gentle) 0.05f else 1f, if (gentle) 0.05f else 1f)
            mp.start()
            if (gentle) handler.post(rise)
            return
        }
    }

    private fun prepare(uri: Uri, attrs: AudioAttributes): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setWakeMode(this@AlarmRingService, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AlarmRingService, uri)
            isLooping = true
            prepare()
        }
    } catch (_: Exception) {
        null
    }

    private val rise = object : Runnable {
        override fun run() {
            val p = ((System.currentTimeMillis() - startedAt).toFloat() / RISE_MS).coerceIn(0f, 1f)
            val volume = 0.05f + 0.95f * p * p
            player?.setVolume(volume, volume)
            if (p < 1f) handler.postDelayed(this, 1_000)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 1_000), 0), attrs)
    }

    private fun snooze() {
        val ring = current ?: return stopRinging()
        val at = container.clock.now() + ring.snoozeMinutes * 60_000L
        AlarmScheduler(this, container.clock).scheduleSnooze(ring.alarmId, at)
        stopRinging()
    }

    private fun timeout() {
        val ring = current
        stopRinging()
        if (ring != null) {
            val note = NotificationCompat.Builder(this, Notifications.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Missed alarm")
                .setContentText(ring.label.ifBlank { "Alarm" })
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM_MISSED, note)
        }
    }

    private fun silence() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopRinging() {
        silence()
        current = null
        state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        silence()
        state.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SNOOZE = "app.cove.companion.alarm.SNOOZE"
        const val ACTION_STOP = "app.cove.companion.alarm.STOP"
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_GENTLE = "gentle"
        private const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        /** Volume ramp length for "Gentle wake". */
        const val RISE_MS = 2 * 60_000L

        /** A ring that nobody answers ends after this long. */
        const val TIMEOUT_MS = 10 * 60_000L

        private val state = MutableStateFlow<RingState?>(null)

        /** The alarm currently ringing, or null. */
        val ringing: StateFlow<RingState?> = state.asStateFlow()

        /** Starts ringing for [alarm]; must be called while the app may start foreground services (alarm broadcast). */
        fun start(context: Context, alarm: AlarmEntity) {
            val intent = Intent(context, AlarmRingService::class.java)
                .putExtra(EXTRA_ID, alarm.id)
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_MINUTES, alarm.minutes)
                .putExtra(EXTRA_SOUND, alarm.sound)
                .putExtra(EXTRA_GENTLE, alarm.gentleRise)
                .putExtra(EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
            context.startForegroundService(intent)
        }

        /** Snoozes the ringing alarm. */
        fun snooze(context: Context) {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_SNOOZE))
        }

        /** Stops the ringing alarm. */
        fun stop(context: Context) {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP))
        }
    }
}
