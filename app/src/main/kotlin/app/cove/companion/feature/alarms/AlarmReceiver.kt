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
import app.cove.companion.data.local.entity.AlarmEntity
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fire(app, id, snooze)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(app: Context, id: String, snooze: Boolean) {
        val container = app.container
        val alarm = container.database.alarms().get(id)?.takeIf { it.deletedAt == null } ?: return
        if (!snooze && !alarm.enabled) return
        if (alarm.kind == "bedtime") postWindDown(app, alarm) else AlarmRingService.start(app, alarm)
        if (snooze) return
        if (alarm.daysMask == 0) {
            container.plan.saveAlarm(alarm.copy(enabled = false))
        } else {
            AlarmScheduler(app, container.clock).schedule(alarm)
        }
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
