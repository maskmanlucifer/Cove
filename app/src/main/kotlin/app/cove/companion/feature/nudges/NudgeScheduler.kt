package app.cove.companion.feature.nudges

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps AlarmManager in step with the user's nudge mode, to-dos and events: one inexact alarm for the
 * next bundled summary (bundled mode only) and one alarm per upcoming reminder.
 * Reminders are explicit requests, so they fire in every mode; the mode only governs summaries.
 */
class NudgeScheduler(context: Context, private val c: AppContainer) {
    private val ctx = context.applicationContext
    private val manager = ctx.getSystemService(AlarmManager::class.java)
    private val prefs = ctx.getSharedPreferences("nudge_scheduler", Context.MODE_PRIVATE)

    /** Starts observing the same Room flows as the UI; call once from the Application. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(c.settings.settings.map { it.nudgeMode }.distinctUntilChanged(), c.todos.todos, events()) { mode, todos, events ->
                Triple(mode, todos, events)
            }.debounce(500).collect { (mode, todos, events) ->
                syncBundle(mode)
                syncReminders(todos, events)
            }
        }
    }

    /** One-shot resync from the database, for boot, clock changes and the periodic worker. */
    suspend fun syncNow() {
        syncBundle(c.settings.settings.first().nudgeMode)
        syncReminders(c.todos.todos.first(), events().first())
    }

    private fun events(): Flow<List<EventEntity>> {
        val farDay = c.clock.now().toLocalDate().plusYears(100)
        return combine(c.plan.upcomingEvents(200), c.plan.repeatingEvents(farDay)) { a, b -> (a + b).distinctBy { it.id } }
    }

    /** Registers the next summary when [mode] is bundled and cancels it otherwise. */
    fun syncBundle(mode: String, after: Long = c.clock.now()) {
        if (mode != MODE_BUNDLED) return manager.cancel(bundlePending())
        scheduleBundleAt(NudgeMath.nextBundleAt(after))
    }

    /** Registers the summary after the one that just fired; skips 30 minutes so an early inexact alarm cannot repeat itself. */
    suspend fun scheduleAfterBundle() {
        syncBundle(c.settings.settings.first().nudgeMode, c.clock.now() + 30 * 60_000L)
    }

    /** Registers a summary at [at]; inexact by default (the system may batch it, which suits a gentle nudge). */
    fun scheduleBundleAt(at: Long, exact: Boolean = false) {
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC, at, bundlePending())
        else manager.setAndAllowWhileIdle(AlarmManager.RTC, at, bundlePending())
    }

    /** Makes the registered reminders match what [ReminderPlanner] says should exist. */
    @Synchronized
    fun syncReminders(todos: List<TodoEntity>, events: List<EventEntity>) {
        val plans = ReminderPlanner.plan(todos, events, c.clock.now())
        val keys = plans.map { it.key }.toSet()
        prefs.getStringSet(KEY_REGISTERED, emptySet()).orEmpty().filter { it !in keys }.forEach { manager.cancel(reminderPending(it, snooze = false)) }
        plans.forEach { register(it.fireAt, reminderPending(it.key, false, it.startAt, it.leadMinutes)) }
        prefs.edit().putStringSet(KEY_REGISTERED, keys).apply()
    }

    /** Cancels the bundled summary and every registered reminder and snooze, and forgets them ("Clear all data"). */
    @Synchronized
    fun cancelAll() {
        manager.cancel(bundlePending())
        prefs.getStringSet(KEY_REGISTERED, emptySet()).orEmpty().forEach {
            manager.cancel(reminderPending(it, snooze = false))
            manager.cancel(reminderPending(it, snooze = true))
        }
        prefs.edit().clear().apply()
    }

    /** Fires the reminder [key] again at [at] (Snooze). */
    fun scheduleSnooze(key: String, at: Long) = register(at, reminderPending(key, snooze = true))

    private fun register(at: Long, pending: PendingIntent) {
        if (manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun bundlePending(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, NudgeReceiver::class.java).setAction(NudgeReceiver.ACTION_BUNDLE).setData(Uri.parse("cove-nudge://bundle")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun reminderPending(key: String, snooze: Boolean, startAt: Long = 0, lead: Int = 0): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, NudgeReceiver::class.java).setAction(NudgeReceiver.ACTION_REMINDER)
            .setData(Uri.parse("cove-nudge://${if (snooze) "snooze/" else "reminder/"}$key"))
            .putExtra(NudgeReceiver.EXTRA_KEY, key).putExtra(NudgeReceiver.EXTRA_SNOOZE, snooze)
            .putExtra(NudgeReceiver.EXTRA_START, startAt).putExtra(NudgeReceiver.EXTRA_LEAD, lead),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val KEY_REGISTERED = "registered_keys"
        const val MODE_BUNDLED = "bundled"
    }
}
