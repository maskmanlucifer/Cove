package app.cove.companion.feature.habits

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Whether each of the 7 days ending on [today] (oldest first) has a log.
 * Habits never reset: missing a day only leaves that dot empty.
 */
fun lastSevenDays(today: LocalDate, loggedEpochDays: Set<Long>): List<Boolean> =
    (6 downTo 0).map { (today.toEpochDay() - it) in loggedEpochDays }

/** "5 of 7 days". */
fun countText(days: List<Boolean>): String = "${days.count { it }} of 7 days"

/** Day-of-week bit for a [daysMask] (Monday = bit 0), matching alarms. */
fun maskBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

/** True when a habit with [cadence] and [daysMask] is expected on [date]. */
fun isDue(cadence: String, daysMask: Int, date: LocalDate): Boolean =
    cadence == "daily" || daysMask and maskBit(date.dayOfWeek) != 0
