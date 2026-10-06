package app.cove.companion.feature.nudges

import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.ZoneId

/** A reminder to register: [key] is `todo:<id>` or `event:<id>`; [startAt] is the due time or event start. */
data class ReminderPlan(val key: String, val fireAt: Long, val startAt: Long, val leadMinutes: Int) {
    val isEvent get() = key.startsWith("event:")
    val id get() = key.substringAfter(':')
}

/** Decides which reminders should exist right now. */
object ReminderPlanner {
    /** AlarmManager caps pending alarms per app, so only the soonest reminders are registered. */
    const val MAX_PENDING = 40

    /** Soonest-first reminders for open to-dos with `remind` and events with `remindBeforeMin`; past ones are dropped. */
    fun plan(
        todos: List<TodoEntity>,
        events: List<EventEntity>,
        now: Long,
        quiet: QuietHours? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderPlan> {
        val fromTodos = todos.filter { it.remind && !it.done && it.deletedAt == null && it.dueAt != null }.mapNotNull {
            NudgeMath.reminderFireAt(it.dueAt!!, 0, now)?.let { fire -> ReminderPlan("todo:${it.id}", fire, it.dueAt, 0) }
        }
        val fromEvents = events.filter { it.deletedAt == null && it.remindBeforeMin != null }.mapNotNull {
            val lead = it.remindBeforeMin!!
            NudgeMath.nextOccurrence(it.startAt, it.repeat, lead, now, zone)?.let { start ->
                ReminderPlan("event:${it.id}", start - lead.coerceAtLeast(0) * 60_000L, start, lead)
            }
        }
        return (fromTodos + fromEvents)
            .map { it.copy(fireAt = NudgeMath.deferPastQuiet(it.fireAt, quiet, zone)) }
            .sortedBy { it.fireAt }
            .take(MAX_PENDING)
    }
}
