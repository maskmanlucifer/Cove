package app.cove.companion.feature.training.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A finished session as the schedule sees it. */
data class DoneSession(val dayType: String, val date: LocalDate)

/** A planned session: [dayType] on [date]. */
data class Slot(val date: LocalDate, val dayType: String)

/**
 * Weekly rhythm: the programme days rotate (Push, Pull, Legs, Push ...) and are placed on the chosen weekdays.
 * A skipped day simply leaves the same day type next in line, so nothing is lost and nothing piles up.
 */
object Schedule {
    /** The day type after [last] in [order] (the first when [last] is null or unknown). */
    fun nextType(order: List<String>, last: String?): String {
        if (order.isEmpty()) return ""
        val i = order.indexOfFirst { it.equals(last, ignoreCase = true) }
        return order[(i + 1) % order.size]
    }

    /** ISO day numbers "1,3,5" to weekdays; empty or invalid input gives [fallback]. */
    fun parseWeekdays(text: String, fallback: Set<DayOfWeek> = defaultWeekdays(3)): Set<DayOfWeek> =
        text.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSet().ifEmpty { fallback }

    /** Weekdays as stored text. */
    fun formatWeekdays(days: Set<DayOfWeek>): String = days.sortedBy { it.value }.joinToString(",") { it.value.toString() }

    /** Sensible default weekdays for [perWeek] sessions (2 to 4). */
    fun defaultWeekdays(perWeek: Int): Set<DayOfWeek> = when (perWeek.coerceIn(2, 4)) {
        2 -> setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        3 -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        else -> setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }

    /**
     * The next [count] sessions from [today]: today counts while it is a training day with nothing done yet.
     *
     * @param done finished sessions in any order; the latest one of a programme day decides which day type is next.
     */
    fun upcoming(today: LocalDate, weekdays: Set<DayOfWeek>, order: List<String>, done: List<DoneSession>, count: Int): List<Slot> {
        if (order.isEmpty() || weekdays.isEmpty() || count <= 0) return emptyList()
        val last = done.maxByOrNull { it.date }
        val lastInRotation = done.filter { d -> order.any { it.equals(d.dayType, true) } }.maxByOrNull { it.date }
        var type = nextType(order, lastInRotation?.dayType)
        var date = if (last?.date == today) today.plusDays(1) else today
        val out = ArrayList<Slot>()
        var guard = 0
        while (out.size < count && guard++ < 400) {
            if (date.dayOfWeek in weekdays) {
                out += Slot(date, type)
                type = nextType(order, type)
            }
            date = date.plusDays(1)
        }
        return out
    }

    /** True when a training day passed since the last session without one, and today is not a training day. */
    fun missedRecently(today: LocalDate, weekdays: Set<DayOfWeek>, done: List<DoneSession>): Boolean {
        if (today.dayOfWeek in weekdays) return false
        val last = done.maxOfOrNull { it.date } ?: return false
        var d = today.minusDays(1)
        while (d.isAfter(last) && d.isAfter(today.minusDays(7))) {
            if (d.dayOfWeek in weekdays) return true
            d = d.minusDays(1)
        }
        return false
    }

    /** "Today", "Tomorrow", or the weekday name ("Thursday"); a date a week or more away adds the day number. */
    fun dayLabel(today: LocalDate, date: LocalDate): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + date.dayOfMonth
    }

    /**
     * The "Next" line of the Training home: "Rest, then legs on Thursday", "Legs tomorrow" or "Legs on Thursday".
     *
     * @param after the session that follows the one shown (or the next one when none is planned today).
     * @param restFirst true when the user is looking at today's session, so the line starts from what happens after it.
     */
    fun nextLine(today: LocalDate, after: Slot?, restFirst: Boolean): String {
        if (after == null) return "Nothing planned"
        val type = after.dayType.lowercase()
        val label = dayLabel(today, after.date)
        return when {
            label == "Today" -> after.dayType + " today"
            label == "Tomorrow" -> after.dayType + " tomorrow"
            restFirst -> "Rest, then $type on $label"
            else -> after.dayType + " on $label"
        }
    }
}
