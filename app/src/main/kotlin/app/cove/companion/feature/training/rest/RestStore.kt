package app.cove.companion.feature.training.rest

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.cove.companion.MainActivity
import app.cove.companion.R
import app.cove.companion.container
import app.cove.companion.core.Notifications
import app.cove.companion.feature.training.engine.RestState
import app.cove.companion.resilience.CrashHandler

/** The rest in progress, kept in preferences as an absolute end time so it survives the screen turning off and process death. */
class RestStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("training_rest", Context.MODE_PRIVATE)

    /** The current rest, or null when none runs. */
    fun get(): RestState? {
        val end = prefs.getLong(KEY_END, 0)
        if (end == 0L) return null
        return RestState(end, prefs.getString(KEY_NEXT, "").orEmpty(), prefs.getString(KEY_HEADER, "").orEmpty())
    }

    fun set(state: RestState) {
        prefs.edit().putLong(KEY_END, state.endsAt).putString(KEY_NEXT, state.nextLabel).putString(KEY_HEADER, state.header).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_END = "end"
        const val KEY_NEXT = "next"
        const val KEY_HEADER = "header"
    }
}

/** Schedules and cancels the "rest is over" alarm; the receiver posts a gentle notification when the app is not on screen. */
object RestAlarm {
    private const val ACTION = "app.cove.companion.training.REST_OVER"
    const val NOTIFICATION_ID = 4301

    private fun pending(ctx: Context, next: String = ""): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, RestReceiver::class.java).setAction(ACTION).setData(Uri.parse("cove-training://rest")).putExtra("next", next),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Rings at [state]'s end time, exactly when the phone allows it. */
    fun schedule(ctx: Context, state: RestState) {
        val app = ctx.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java)
        runCatching {
            if (manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.endsAt, pending(app, state.nextLabel))
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.endsAt, pending(app, state.nextLabel))
        }.onFailure { CrashHandler.report("rest-alarm", it) }
    }

    /** Cancels the alarm and any posted notification. */
    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        app.getSystemService(AlarmManager::class.java).cancel(pending(app))
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

/** Fires when a rest ends: one soft buzz, and a notification only when the app is not in front. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val next = intent.getStringExtra("next").orEmpty()
        CrashHandler.guarded("rest-receiver") {
            if (RestStore(app).get() != null) {
                vibrate(app)
                if (!app.container.foreground.isForeground()) post(app, next)
            }
        }
    }

    private fun vibrate(app: Context) {
        runCatching {
            app.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1))
        }
    }

    private fun post(app: Context, next: String) {
        Notifications.createChannels(app)
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest is over")
            .setContentText(if (next.isBlank()) "Ready when you are." else "Next · $next")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        runCatching { app.getSystemService(NotificationManager::class.java).notify(RestAlarm.NOTIFICATION_ID, n) }
    }
}
