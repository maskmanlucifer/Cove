package app.cove.companion.feature.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.cove.companion.MainActivity
import app.cove.companion.R
import app.cove.companion.container
import app.cove.companion.core.Notifications
import app.cove.companion.core.clockText
import app.cove.companion.core.Clock
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the AlarmManager trigger. Wake alarms start [AlarmRingService]; bedtime alarms post a
 * gentle "Wind down" notification. Then it re-registers repeating alarms or disables one-time ones.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("alarm")).launch {
            try {
                fire(app, id, snooze)
            } finally {
                pending.finish()
            }
        }
    }

    /** Rings from the database; if it cannot be read, rings from the [AlarmMirror] so the alarm is never lost. */
    private suspend fun fire(app: Context, id: String, snooze: Boolean) {
        val alarm = try {
            val container = app.container
            val row = container.database.alarms().get(id)?.takeIf { it.deletedAt == null } ?: return
            if (!snooze && !row.enabled) return
            row
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashHandler.report("alarm-db", e)
            return fireBlind(app, id, snooze)
        }
        sound(app, alarm)
        if (snooze) return
        runCatching {
            val container = app.container
            if (alarm.daysMask == 0) container.plan.saveAlarm(alarm.copy(enabled = false)) else AlarmScheduler(app, container.clock).schedule(alarm)
        }.onFailure { CrashHandler.report("alarm-reschedule", it) }
    }

    /** No database: ring from the mirror (or a plain "Alarm" if even that is gone) and re-register repeating alarms. */
    private fun fireBlind(app: Context, id: String, snooze: Boolean) {
        val mirror = AlarmMirror.forContext(app)
        val saved = mirror.find(id)
        if (saved != null && !snooze && !saved.enabled) return
        val alarm = saved?.toEntity() ?: AlarmEntity(id, "", Clock.System.now().let { minutesOfDay(it) }, 0)
        sound(app, alarm)
        if (snooze || saved == null) return
        runCatching {
            if (alarm.daysMask == 0) mirror.remove(id) else AlarmScheduler(app, Clock.System).schedule(alarm)
        }.onFailure { CrashHandler.report("alarm-reschedule", it) }
    }

    private fun sound(app: Context, alarm: AlarmEntity) {
        try {
            if (alarm.kind == "bedtime") postWindDown(app, alarm) else AlarmRingService.start(app, alarm)
        } catch (e: Exception) {
            CrashHandler.report("alarm-ring", e)
        }
    }

    private fun minutesOfDay(millis: Long): Int {
        val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun postWindDown(context: Context, alarm: AlarmEntity) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = clockText(alarm.minutes)
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_NUDGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Wind down")
            .setContentText("It is ${time.digits}${time.suffix}. Time to let the screen dim.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_WIND_DOWN, notification)
    }

    companion object {
        const val ACTION_FIRE = "app.cove.companion.alarm.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}
