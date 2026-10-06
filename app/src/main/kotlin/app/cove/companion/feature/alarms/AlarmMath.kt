package app.cove.companion.feature.alarms

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Helpers for `AlarmEntity.daysMask`: bit 0 = Monday … bit 6 = Sunday, 0 = once. */
object AlarmDays {
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    private val short = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Whether [mask] includes [day]. */
    fun has(mask: Int, day: DayOfWeek): Boolean = (mask shr (day.value - 1)) and 1 == 1

    /** [mask] with the bit for [index] (0 = Monday) flipped. */
    fun flip(mask: Int, index: Int): Int = mask xor (1 shl index)

    /** Lower-case description used after the label: "once", "every day", "weekdays", "weekends" or "Mon, Wed". */
    fun describe(mask: Int): String = when (mask and ALL) {
        0 -> "once"
        ALL -> "every day"
        WEEKDAYS -> "weekdays"
        WEEKEND -> "weekends"
        else -> short.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(", ")
    }
}

/**
 * Next time an alarm at [minutes] since midnight fires strictly after [nowMillis].
 * [daysMask] 0 means once (today if still ahead, else tomorrow). Uses java.time so DST gaps shift
 * forward and overlaps pick the earlier instant.
 */
fun nextFireMillis(minutes: Int, daysMask: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    for (offset in 0L..8L) {
        val date = today.plusDays(offset)
        if (daysMask and AlarmDays.ALL != 0 && !AlarmDays.has(daysMask, date.dayOfWeek)) continue
        val at = ZonedDateTime.of(date, java.time.LocalTime.of((minutes / 60) % 24, minutes % 60), zone).toInstant().toEpochMilli()
        if (at > nowMillis) return at
    }
    error("unreachable: an alarm always has a fire time within eight days")
}

/** "8 h 49 min", "49 min" or "2 d 3 h" for a positive delay; rounds partial minutes up. */
fun untilText(deltaMillis: Long): String {
    val total = (deltaMillis + 59_999) / 60_000
    val d = total / 1440
    val h = total % 1440 / 60
    val m = total % 60
    return when {
        d > 0 -> "$d d $h h"
        h > 0 -> if (m == 0L) "$h h" else "$h h $m min"
        else -> "$m min"
    }
}

/** Subtitle under a list time: "Wake up · weekdays", or just the label when it already names the days. */
fun alarmSubtitle(label: String, daysMask: Int): String {
    val days = AlarmDays.describe(daysMask)
    return when {
        label.isBlank() -> days.replaceFirstChar { it.uppercase() }
        label.equals(days, ignoreCase = true) -> label
        else -> "$label · $days"
    }
}
