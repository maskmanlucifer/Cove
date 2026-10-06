package app.cove.companion.feature.nudges

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A daily window (minutes since midnight) in which nudges wait; may wrap past midnight. */
data class QuietHours(val startMinutes: Int, val endMinutes: Int)

/** Pure time arithmetic for nudges and reminders; the clock and zone are always passed in. */
object NudgeMath {
    /** Hours of day at which the bundled summary may arrive. */
    val BUNDLE_HOURS = listOf(9, 13, 18)

    const val SNOOZE_MINUTES = 10

    /** First bundle time strictly after [now]. */
    fun nextBundleAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val today = local.toLocalDate()
        val next = BUNDLE_HOURS.map { today.atTime(it, 0) }.firstOrNull { it.isAfter(local) }
            ?: today.plusDays(1).atTime(BUNDLE_HOURS.first(), 0)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    /** When a reminder for something starting at [startAt] fires, or null when that moment is not after [now]. */
    fun reminderFireAt(startAt: Long, leadMinutes: Int, now: Long): Long? =
        (startAt - leadMinutes.coerceAtLeast(0) * 60_000L).takeIf { it > now }

    fun snoozeAt(now: Long): Long = now + SNOOZE_MINUTES * 60_000L

    /**
     * Start of the first occurrence of something that began at [startAt] and repeats ([repeat] is
     * none|daily|weekly|monthly) whose reminder [leadMinutes] before it is still after [now].
     */
    fun nextOccurrence(startAt: Long, repeat: String, leadMinutes: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (reminderFireAt(startAt, leadMinutes, now) != null) return startAt
        val unit = when (repeat) {
            "daily" -> ChronoUnit.DAYS
            "weekly" -> ChronoUnit.WEEKS
            "monthly" -> ChronoUnit.MONTHS
            else -> return null
        }
        val start = Instant.ofEpochMilli(startAt).atZone(zone).toLocalDateTime()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var k = (unit.between(start.toLocalDate(), today) - 1).coerceAtLeast(1)
        while (true) {
            val at = start.plus(k, unit).atZone(zone).toInstant().toEpochMilli()
            if (reminderFireAt(at, leadMinutes, now) != null) return at
            k++
        }
    }

    /** Moves [at] to the end of [quiet] when it falls inside it; unchanged without quiet hours. */
    fun deferPastQuiet(at: Long, quiet: QuietHours?, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (quiet == null) return at
        val local = Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime()
        val minute = local.hour * 60 + local.minute
        val wraps = quiet.startMinutes > quiet.endMinutes
        val inside = if (wraps) minute >= quiet.startMinutes || minute < quiet.endMinutes else minute in quiet.startMinutes until quiet.endMinutes
        if (!inside) return at
        val endDay = if (wraps && minute >= quiet.startMinutes) local.toLocalDate().plusDays(1) else local.toLocalDate()
        return LocalDateTime.of(endDay, java.time.LocalTime.of(quiet.endMinutes / 60, quiet.endMinutes % 60)).atZone(zone).toInstant().toEpochMilli()
    }
}
