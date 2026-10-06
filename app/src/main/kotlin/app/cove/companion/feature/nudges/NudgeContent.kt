package app.cove.companion.feature.nudges

import app.cove.companion.core.clockText
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.Instant
import java.time.ZoneId

/** One thing for a summary: [at] is null for to-dos without a time. */
data class NudgeItem(val title: String, val at: Long?)

/** Text of a bundled summary notification. */
data class BundleContent(val title: String, val body: String, val count: Int)

/** Reminder notification text. */
data class ReminderText(val title: String, val text: String)

/** Builds the wording of nudges and reminders; pure so it can be tested. */
object NudgeContent {
    /** Name of the stretch of day that a summary at [hour] covers. */
    fun windowName(hour: Int) = when {
        hour < 12 -> "today"
        hour < 17 -> "this afternoon"
        else -> "this evening"
    }

    /**
     * To-dos and events still ahead today. Undated to-dos only count in the morning summary
     * ([includeUndated]) so later summaries do not repeat the same chores.
     */
    fun items(
        todos: List<TodoEntity>,
        events: List<EventEntity>,
        now: Long,
        includeUndated: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NudgeItem> {
        val endOfDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todoItems = todos.filter { !it.done && it.deletedAt == null }.mapNotNull {
            when {
                it.dueAt == null -> if (includeUndated) NudgeItem(it.title, null) else null
                it.dueAt in now until endOfDay -> NudgeItem(it.title, it.dueAt)
                else -> null
            }
        }
        val eventItems = events.filter { it.deletedAt == null }.mapNotNull {
            NudgeMath.nextOccurrence(it.startAt, it.repeat, 0, now - 1, zone)?.takeIf { at -> at < endOfDay }?.let { at -> NudgeItem(it.title, at) }
        }
        return (todoItems + eventItems).sortedWith(compareBy(nullsLast()) { it.at })
    }

    /** Summary wording, or null when there is nothing to say (a calm app posts nothing). */
    fun bundle(items: List<NudgeItem>, hour: Int, oneThing: Boolean): BundleContent? {
        if (items.isEmpty()) return null
        val window = windowName(hour)
        if (oneThing) return BundleContent("One thing $window", items.first().title, 1)
        val shown = items.take(3).mapIndexed { i, item -> if (i == 0) item.title else item.title.lowerFirst() }
        val more = items.size - shown.size
        val body = shown.joinToString(", ") + if (more > 0) ", +$more more" else ""
        return BundleContent("${items.size} for $window", body, items.size)
    }

    /** Text for a to-do or event reminder; [startAt] is the due time or the event start. */
    fun reminder(title: String, startAt: Long, leadMinutes: Int, place: String?, isEvent: Boolean): ReminderText {
        val time = startAt.toLocalDateTime().let { clockText(it.hour * 60 + it.minute) }.let { it.digits + it.suffix }
        val text = when {
            !isEvent -> "Reminder · $time"
            leadMinutes <= 0 -> "Starting now"
            else -> "In $leadMinutes min · $time"
        }
        return ReminderText(title, listOfNotNull(text, place).joinToString(" · "))
    }

    /** Lower-cases the first letter so a list reads as one sentence, unless the word is an acronym or name-like (`iPhone`, `ID`). */
    private fun String.lowerFirst(): String =
        if (length > 1 && this[0].isUpperCase() && this[1].isLowerCase()) this[0].lowercaseChar() + substring(1) else this
}
