package app.cove.companion.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.cove.companion.AppContainer
import app.cove.companion.feature.nudges.NudgeScheduler
import app.cove.companion.feature.nudges.Nudges

/** Debug-only intent hooks for widgets and nudges; call only inside `BuildConfig.DEBUG`. */
object DebugWidgets {
    /**
     * `--es pin_widget next|voice|spent|tasks` asks the launcher to pin that widget;
     * `--ei nudge_in_sec N` schedules the bundled summary N seconds from real time (after the scheduler's own startup sync);
     * `--ei reminder_in_sec N` adds a to-do "Test reminder" due N real seconds from now with a reminder;
     * `--ez brief_ready true` posts the brief-ready notification.
     */
    suspend fun handle(context: Context, c: AppContainer, intent: Intent) {
        intent.getStringExtra("pin_widget")?.let { which ->
            val receiver = when (which) {
                "next" -> NextWidgetReceiver::class.java
                "voice" -> VoiceWidgetReceiver::class.java
                "spent" -> SpentWidgetReceiver::class.java
                else -> TasksWidgetReceiver::class.java
            }
            context.getSystemService(AppWidgetManager::class.java).requestPinAppWidget(ComponentName(context, receiver), null, null)
        }
        val nudgeIn = intent.getIntExtra("nudge_in_sec", -1)
        if (nudgeIn >= 0) {
            kotlinx.coroutines.delay(2_000)
            NudgeScheduler(context, c).scheduleBundleAt(System.currentTimeMillis() + nudgeIn * 1000L, exact = true)
        }
        val reminderIn = intent.getIntExtra("reminder_in_sec", -1)
        if (reminderIn >= 0) {
            val todo = c.todos.add("Test reminder", null, System.currentTimeMillis() + reminderIn * 1000L)
            c.todos.save(todo.copy(id = todo.id, remind = true))
        }
        if (intent.getBooleanExtra("brief_ready", false)) Nudges.briefReady(context)
    }
}
