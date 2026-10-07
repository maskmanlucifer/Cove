package app.cove.companion.feature.plan

import app.cove.companion.core.clockText
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.LocalDate

/** Where a schedule line comes from. */
enum class ItemKind { Alarm, Event, Todo }

/** One line of the day before it is laid out: a time (minutes since midnight) plus what happens then. */
data class ScheduleItem(
    val kind: ItemKind,
    val minutes: Int,
    val endMinutes: Int?,
    val title: String,
    val notes: String? = null,
    val done: Boolean = false,
    val todoId: String? = null,
    val eventId: String? = null,
    val alarmId: String? = null,
)

/** A row of the schedule list. */
sealed interface TimelineRow {
    /**
     * [card] marks the next event, which gets a white card with its length instead of an "until" line.
     * [current] marks the one entry that is happening now or comes next; its time is drawn in the accent colour.
     */
    data class Entry(val item: ScheduleItem, val past: Boolean, val card: Boolean, val detail: String?, val current: Boolean = false) : TimelineRow
}

/** True when [alarm] rings on [day]; `daysMask` bit 0 is Monday and 0 means once. */
fun alarmRingsOn(alarm: AlarmEntity, day: LocalDate): Boolean =
    alarm.enabled && alarm.deletedAt == null &&
        (alarm.daysMask == 0 || alarm.daysMask and (1 shl (day.dayOfWeek.value - 1)) != 0)

/**
 * True when [event] has an occurrence on [day], counting its repeat rule
 * (daily, weekly, monthly) from the first occurrence.
 */
fun eventOccursOn(event: EventEntity, day: LocalDate): Boolean {
    if (event.deletedAt != null) return false
    val first = event.startAt.toLocalDateTime().toLocalDate()
    if (day < first) return false
    return when (event.repeat) {
        "daily" -> true
        "weekly" -> day.dayOfWeek == first.dayOfWeek
        "monthly" -> day.dayOfMonth == first.dayOfMonth
        else -> day == first
    }
}

/** Merges events, alarms and dated to-dos for [day] into time-ordered [ScheduleItem]s. */
fun scheduleItems(
    day: LocalDate,
    events: List<EventEntity>,
    alarms: List<AlarmEntity>,
    todos: List<TodoEntity>,
): List<ScheduleItem> {
    val start = day.startOfDayMillis()
    val end = day.plusDays(1).startOfDayMillis()
    val fromEvents = events.filter { eventOccursOn(it, day) }.map { e ->
        val s = e.startAt.toLocalDateTime()
        val len = e.endAt?.let { ((it - e.startAt) / 60_000).toInt() }
        val startMin = s.hour * 60 + s.minute
        ScheduleItem(ItemKind.Event, startMin, len?.let { startMin + it }, e.title, e.notes ?: e.place, eventId = e.id)
    }
    val fromAlarms = alarms.filter { alarmRingsOn(it, day) }.map {
        ScheduleItem(ItemKind.Alarm, it.minutes, null, if (it.kind == "bedtime") "Wind down" else it.label, alarmId = it.id)
    }
    val fromTodos = todos.filter { it.deletedAt == null && it.dueAt != null && it.dueAt >= start && it.dueAt < end }
        .mapNotNull { t ->
            val d = t.dueAt!!.toLocalDateTime()
            val min = d.hour * 60 + d.minute
            if (min == 0) null else ScheduleItem(ItemKind.Todo, min, null, t.title, done = t.done, todoId = t.id)
        }
    return (fromEvents + fromAlarms + fromTodos).sortedBy { it.minutes }
}

/**
 * Lays [items] out for the schedule at [nowMinutes]: finished items are marked past and the next event becomes the card.
 * With [markCurrent] (today only) the event in progress, or else the first item that has not started yet, is marked
 * [TimelineRow.Entry.current] so its time can stand out; there is no separate "now" line.
 */
fun buildTimeline(items: List<ScheduleItem>, nowMinutes: Int, markCurrent: Boolean = true): List<TimelineRow.Entry> {
    val sorted = items.sortedBy { it.minutes }
    val cardIndex = sorted.indexOfFirst { it.kind == ItemKind.Event && (it.endMinutes ?: it.minutes) > nowMinutes }
    val currentIndex = if (!markCurrent) -1 else
        sorted.indexOfFirst { it.kind == ItemKind.Event && it.minutes <= nowMinutes && (it.endMinutes ?: it.minutes) > nowMinutes }
            .takeIf { it >= 0 } ?: sorted.indexOfFirst { it.minutes > nowMinutes }
    return sorted.mapIndexed { i, item ->
        val past = when (item.kind) {
            ItemKind.Alarm -> item.minutes < nowMinutes
            ItemKind.Event -> (item.endMinutes ?: item.minutes) <= nowMinutes
            ItemKind.Todo -> item.done
        }
        val card = i == cardIndex
        TimelineRow.Entry(item, past, card, detailFor(item, card), current = i == currentIndex)
    }
}

private fun detailFor(item: ScheduleItem, card: Boolean): String? {
    val length = item.endMinutes?.let { end ->
        if (card) durationText(end - item.minutes) else "until " + hourText(end)
    }
    return listOfNotNull(length, item.notes?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
}

/** "45 min", "1 h", "1 h 30 min". */
fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Clock digits without the am/pm suffix and without ":00", e.g. 960 -> "4", 990 -> "4:30". */
fun hourText(minutes: Int): String = clockText(minutes).digits.removeSuffix(":00")
