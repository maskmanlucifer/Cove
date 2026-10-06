package app.cove.companion.ai.provider.rules

import app.cove.companion.core.toEpochMillis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A clock time found in a transcript; [meridiem] is null when the speaker did not say am/pm. */
data class TimeMatch(val hour: Int, val minute: Int, val meridiem: Boolean?, val range: IntRange)

/** A calendar day or relative offset found in a transcript. */
data class DayMatch(val date: LocalDate, val range: IntRange)

/** Time phrases of spoken English, on text already passed through [SpokenNumbers.digitize]. */
object WhenParser {
    private const val AMPM = "(a\\.?m\\.?|p\\.?m\\.?)"
    private val opts = setOf(RegexOption.IGNORE_CASE)
    private val half = Regex("\\b(?:at\\s+|for\\s+|by\\s+)?(half|quarter)\\s+(past|after|to)\\s+(\\d{1,2})\\b", opts)
    private val colon = Regex("\\b(?:at\\s+|for\\s+|by\\s+)?(\\d{1,2}):(\\d{2})\\s*$AMPM?", opts)
    private val spaced = Regex("\\b(?:at|for|by|to|around|@)\\s+(\\d{1,2})[ .](\\d{2})\\b\\s*$AMPM?", opts)
    private val suffixed = Regex("\\b(?:at\\s+|for\\s+|by\\s+)?(\\d{1,2})\\s*$AMPM(?![a-z])", opts)
    private val oclock = Regex("\\b(?:at\\s+|for\\s+|by\\s+)?(\\d{1,2})\\s*o'?\\s?clock\\b", opts)
    private val bare = Regex("\\b(?:at|for|by|to|around|@)\\s+(\\d{1,2})\\b(?!\\s*(?:rupees|rs|%|k\\b|[.,]\\d))", opts)
    private val noon = Regex("\\b(?:at\\s+)?(noon|midday|midnight)\\b", opts)
    private val dayHint = Regex("\\b(in the morning|this morning|in the evening|this evening|in the afternoon|this afternoon|tonight|at night)\\b", opts)

    /** Finds the first clock time; bare hours ("at 4") count only when [allowBareHour]. */
    fun findTime(text: String, allowBareHour: Boolean = true): TimeMatch? {
        half.find(text)?.let { m ->
            val h = m.groupValues[3].toInt()
            if (h in 1..12) {
                val (hour, minute) = when (m.groupValues[1].lowercase() to m.groupValues[2].lowercase()) {
                    "half" to "to" -> (h - 1) to 30
                    "quarter" to "to" -> (h - 1) to 45
                    "quarter" to "past", "quarter" to "after" -> h to 15
                    else -> h to 30
                }
                return TimeMatch(if (hour == 0) 12 else hour, minute, null, m.range)
            }
        }
        colon.find(text)?.let { m -> build(m, 1, 2, 3)?.let { return it } }
        spaced.find(text)?.let { m -> build(m, 1, 2, 3)?.let { return it } }
        suffixed.find(text)?.let { m -> build(m, 1, null, 2)?.let { return it } }
        oclock.find(text)?.let { m -> build(m, 1, null, null)?.let { return it } }
        noon.find(text)?.let { m ->
            val noonHour = if (m.groupValues[1].equals("midnight", true)) 0 else 12
            return TimeMatch(noonHour, 0, true, m.range)
        }
        if (allowBareHour) bare.find(text)?.let { m -> build(m, 1, null, null)?.let { return it } }
        return null
    }

    private fun build(m: MatchResult, hourGroup: Int, minuteGroup: Int?, meridiemGroup: Int?): TimeMatch? {
        val hour = m.groupValues[hourGroup].toInt()
        val minute = minuteGroup?.let { m.groupValues[it].toInt() } ?: 0
        val suffix = meridiemGroup?.let { m.groupValues[it].lowercase() }.orEmpty()
        val meridiem = if (suffix.isEmpty()) null else suffix.startsWith("p")
        if (minute > 59) return null
        if (meridiem != null && hour !in 1..12) return null
        if (hour > 24 || (hour == 24 && minute > 0)) return null
        val h = when {
            meridiem == null -> hour % 24
            meridiem -> if (hour == 12) 12 else hour + 12
            else -> if (hour == 12) 0 else hour
        }
        return TimeMatch(h, minute, meridiem ?: if (hour > 12 || hour == 0) true else null, m.range)
    }

    /** True when the text says morning (false), evening/night (true), or nothing (null). */
    fun partOfDay(text: String): Boolean? {
        val m = dayHint.find(text)?.value?.lowercase() ?: return null
        return !m.contains("morning")
    }

    private val days = DayOfWeek.entries.associateBy { it.name.lowercase() }
    private val dayWord = Regex(
        "\\b(day after tomorrow|tomorrow|today|tonight|(?:on |next |this )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)|in (\\d{1,2}) days?)\\b",
        opts,
    )

    /** Finds "today", "tomorrow", a weekday or "in N days"; null when no day is mentioned. */
    fun findDay(text: String, today: LocalDate): DayMatch? {
        val m = dayWord.find(text) ?: return null
        val whole = m.groupValues[1].lowercase()
        val date = when {
            whole == "day after tomorrow" -> today.plusDays(2)
            whole == "tomorrow" -> today.plusDays(1)
            whole == "today" || whole == "tonight" -> today
            m.groupValues[3].isNotEmpty() -> today.plusDays(m.groupValues[3].toLong())
            else -> {
                val target = days.getValue(m.groupValues[2].lowercase())
                var delta = (target.value - today.dayOfWeek.value + 7) % 7
                if (delta == 0) delta = 7
                today.plusDays(delta.toLong())
            }
        }
        return DayMatch(date, m.range)
    }

    private val relative = Regex("\\bin\\s+(an?|\\d{1,3}|half an)\\s*(minutes?|mins?|hours?|hrs?)\\b", opts)

    /** "in 20 minutes", "in an hour": the resulting instant plus the matched range. */
    fun findRelative(text: String, now: LocalDateTime): Pair<LocalDateTime, IntRange>? {
        val m = relative.find(text) ?: return null
        val n = when (val g = m.groupValues[1].lowercase()) {
            "a", "an" -> 1.0
            "half an" -> 0.5
            else -> g.toDouble()
        }
        val unit = if (m.groupValues[2].lowercase().startsWith("h")) 60.0 else 1.0
        return now.plusMinutes((n * unit).toLong()) to m.range
    }

    /**
     * Alarm minutes since midnight. Without am/pm, 1-11 mean morning unless the text says evening or night.
     */
    fun alarmMinutes(t: TimeMatch, text: String): Int {
        var hour = t.hour
        if (t.meridiem == null && hour in 1..11 && partOfDay(text) == true) hour += 12
        if (t.meridiem == null && hour == 12 && partOfDay(text) == false) hour = 0
        return hour * 60 + t.minute
    }

    /**
     * Reminder instant. Same-day without am/pm picks the next occurrence; other days assume 1-6 mean pm and 7-11 am.
     */
    fun reminderAt(t: TimeMatch, day: LocalDate?, now: LocalDateTime, text: String): Long {
        val date = day ?: now.toLocalDate()
        val hint = partOfDay(text)
        val candidates = if (t.meridiem != null || t.hour >= 12 || t.hour == 0) {
            listOf(t.hour)
        } else if (hint != null) {
            listOf(if (hint) t.hour + 12 else t.hour)
        } else if (day == null || day == now.toLocalDate()) {
            listOf(t.hour, t.hour + 12)
        } else {
            listOf(if (t.hour in 1..6) t.hour + 12 else t.hour)
        }
        val times = candidates.map { LocalDateTime.of(date, LocalTime.of(it % 24, t.minute)) }
        val chosen = times.firstOrNull { !it.isBefore(now) } ?: times.last().let { if (day == null) it.plusDays(1) else it }
        return chosen.toEpochMillis()
    }
}
