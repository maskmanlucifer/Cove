package app.cove.companion.feature.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.cove.companion.MainActivity
import app.cove.companion.core.Clock
import app.cove.companion.data.local.entity.AlarmEntity

/**
 * Registers alarms with [AlarmManager.setAlarmClock] (exact, survives Doze, shown by the system).
 * Each alarm owns one PendingIntent keyed by a data URI; snoozes use a separate key so syncing
 * alarms never cancels a pending snooze. Every [sync] also refreshes the [AlarmMirror], so alarms can still ring
 * when the database is unreadable.
 */
class AlarmScheduler(
    private val context: Context,
    private val clock: Clock,
    private val mirror: AlarmMirror = AlarmMirror.forContext(context),
) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarm_scheduler", Context.MODE_PRIVATE)

    /** Makes AlarmManager match [alarms]: registers enabled ones and cancels ones that were removed or switched off. */
    @Synchronized
    fun sync(alarms: List<AlarmEntity>) {
        mirror.write(alarms)
        val live = alarms.filter { it.enabled && it.deletedAt == null }
        val liveIds = live.map { it.id }.toSet()
        val previous = prefs.getStringSet(KEY_REGISTERED, emptySet()).orEmpty()
        (previous - liveIds).forEach(::cancel)
        live.forEach(::schedule)
        prefs.edit().putStringSet(KEY_REGISTERED, liveIds).apply()
    }

    /** Registers the next occurrence of [alarm]; returns the trigger time. */
    fun schedule(alarm: AlarmEntity): Long {
        val at = nextFireMillis(alarm.minutes, alarm.daysMask, clock.now())
        register(at, firePending(alarm.id, snooze = false))
        return at
    }

    /** Removes the registration for [alarmId]. */
    fun cancel(alarmId: String) {
        manager.cancel(firePending(alarmId, snooze = false))
    }

    /** Removes a pending snooze of [alarmId]. */
    fun cancelSnooze(alarmId: String) {
        manager.cancel(firePending(alarmId, snooze = true))
    }

    /** Re-fires [alarmId] at [atMillis] as a snooze. */
    fun scheduleSnooze(alarmId: String, atMillis: Long) {
        register(atMillis, firePending(alarmId, snooze = true))
    }

    private fun register(at: Long, pending: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending)
        } catch (_: SecurityException) {
            // Exact alarms were revoked: ring as close as the system allows; the Alarms screen tells the user how to fix it.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun firePending(alarmId: String, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("cove-alarm://${if (snooze) "snooze/" else ""}$alarmId"))
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val KEY_REGISTERED = "registered_ids"
    }
}
