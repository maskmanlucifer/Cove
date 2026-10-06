package app.cove.companion.feature.nudges

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.cove.companion.MainActivity
import app.cove.companion.R
import app.cove.companion.container
import app.cove.companion.core.Notifications
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.feature.voice.listenIntent
import kotlinx.coroutines.flow.first

/** Posts the nudge, reminder and brief-ready notifications. Entry point for other features. */
object Nudges {
    /**
     * Tells the user the morning brief is ready (call from the brief worker). Quiet, on the nudges
     * channel, and skipped when the brief is switched off.
     */
    suspend fun briefReady(context: Context) {
        val app = context.applicationContext
        if (!app.container.settings.settings.first().briefOn) return
        val notification = base(app, Notifications.CHANNEL_NUDGES)
            .setContentTitle("Your morning brief is ready")
            .setContentText("A minute to catch up on the day.")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        notify(app, Notifications.ID_BRIEF_READY, notification)
    }

    /** Posts the bundled summary for the current time of day; nothing when the day is clear or the mode is not bundled. */
    suspend fun postBundle(context: Context) {
        val app = context.applicationContext
        val c = app.container
        val settings = c.settings.settings.first()
        if (settings.nudgeMode != "bundled") return
        val now = c.clock.now()
        val hour = now.toLocalDateTime().hour
        val items = NudgeContent.items(c.todos.todos.first(), c.plan.upcomingEvents(50).first(), now, includeUndated = hour < 12)
        val content = NudgeContent.bundle(items, hour, settings.oneThingMode) ?: return
        val notification = base(app, Notifications.CHANNEL_NUDGES)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Open", openIntent(app))
            .addAction(0, "Ask", askIntent(app))
            .build()
        notify(app, Notifications.ID_NUDGE_BUNDLE, notification)
    }

    /**
     * Posts the reminder for [key] unless the to-do is done or gone (or its reminder was switched off,
     * for a regular fire). [startAt]/[lead] come from the registered plan.
     */
    suspend fun postReminder(context: Context, key: String, snooze: Boolean, startAt: Long, lead: Int) {
        val app = context.applicationContext
        val c = app.container
        val id = key.substringAfter(':')
        val text = if (key.startsWith("todo:")) {
            val todo = c.database.todos().get(id)?.takeIf { it.deletedAt == null && !it.done && (snooze || it.remind) } ?: return
            NudgeContent.reminder(todo.title, todo.dueAt ?: startAt, 0, null, isEvent = false)
        } else {
            val event = c.database.events().get(id)?.takeIf { it.deletedAt == null } ?: return
            NudgeContent.reminder(event.title, startAt.takeIf { it > 0 } ?: event.startAt, if (snooze) 0 else lead, event.place, isEvent = true)
        }
        val builder = base(app, Notifications.CHANNEL_REMINDERS)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (key.startsWith("todo:")) builder.addAction(0, "Done", actionIntent(app, NudgeReceiver.ACTION_DONE, key))
        builder.addAction(0, "Snooze 10 min", actionIntent(app, NudgeReceiver.ACTION_SNOOZE, key))
        notify(app, reminderNotificationId(key), builder.build())
    }

    /** Stable notification id for a reminder key. */
    fun reminderNotificationId(key: String) = Notifications.ID_REMINDER_BASE + (key.hashCode() and 0x7FFF)

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(openIntent(context))
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Opens the app straight into listening. Android has no microphone on the lock screen, so this needs an unlock. */
    private fun askIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 1, listenIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun actionIntent(context: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, NudgeReceiver::class.java).setAction(action).setData(Uri.parse("cove-nudge://$action/$key")).putExtra(NudgeReceiver.EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
