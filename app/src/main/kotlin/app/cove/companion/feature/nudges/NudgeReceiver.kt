package app.cove.companion.feature.nudges

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cove.companion.container
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Longest a receiver may wait on the database before giving up (a broken one can leave queries waiting forever). */
private const val HANDLER_TIMEOUT_MS = 8_000L

/** Handles alarm triggers for summaries and reminders, and the Done / Snooze notification actions. */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val key = intent.getStringExtra(EXTRA_KEY)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("nudge")).launch {
            try {
                CrashHandler.guarded("nudge") { withTimeout(HANDLER_TIMEOUT_MS) { handle(app, intent, key) } }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: Context, intent: Intent, key: String?) {
        val c = app.container
        val scheduler = NudgeScheduler(app, c)
        when (intent.action) {
            ACTION_BUNDLE -> {
                Nudges.postBundle(app)
                scheduler.scheduleAfterBundle()
            }
            ACTION_REMINDER -> if (key != null) {
                Nudges.postReminder(app, key, intent.getBooleanExtra(EXTRA_SNOOZE, false), intent.getLongExtra(EXTRA_START, 0), intent.getIntExtra(EXTRA_LEAD, 0))
                scheduler.syncNow()
            }
            ACTION_DONE -> if (key != null) {
                c.todos.setDone(key.substringAfter(':'), true)
                dismiss(app, key)
            }
            ACTION_SNOOZE -> if (key != null) {
                scheduler.scheduleSnooze(key, NudgeMath.snoozeAt(c.clock.now()))
                dismiss(app, key)
            }
        }
    }

    private fun dismiss(app: Context, key: String) =
        app.getSystemService(NotificationManager::class.java).cancel(Nudges.reminderNotificationId(key))

    companion object {
        const val ACTION_BUNDLE = "app.cove.companion.nudge.BUNDLE"
        const val ACTION_REMINDER = "app.cove.companion.nudge.REMINDER"
        const val ACTION_DONE = "app.cove.companion.nudge.DONE"
        const val ACTION_SNOOZE = "app.cove.companion.nudge.SNOOZE"
        const val EXTRA_KEY = "key"
        const val EXTRA_SNOOZE = "snooze"
        const val EXTRA_START = "start"
        const val EXTRA_LEAD = "lead"
    }
}

/** Re-registers nudges after reboot, app update, or clock / time-zone changes. */
class NudgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("nudge-boot")).launch {
            try {
                CrashHandler.guarded("nudge-boot") { withTimeout(HANDLER_TIMEOUT_MS) { NudgeScheduler(app, app.container).syncNow() } }
            } finally {
                pending.finish()
            }
        }
    }
}
