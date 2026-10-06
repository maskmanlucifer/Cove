package app.cove.companion.feature.journal

import java.time.LocalDate
import java.time.YearMonth

/** One calendar cell: [day] is the day of month, [date] the full date. */
data class MonthCell(val day: Int, val date: LocalDate, val hasEntry: Boolean)

/** A month laid out Monday-first; [leading] blank cells precede day 1. */
data class MonthGrid(val month: YearMonth, val leading: Int, val cells: List<MonthCell>) {
    /** Days of the month that have at least one entry. */
    val entryDays: Int get() = cells.count { it.hasEntry }
}

/** Builds the [MonthGrid] for [month] given the epoch days that have entries. */
fun buildMonthGrid(month: YearMonth, entryEpochDays: Set<Long>): MonthGrid = MonthGrid(
    month,
    month.atDay(1).dayOfWeek.value - 1,
    (1..month.lengthOfMonth()).map {
        val date = month.atDay(it)
        MonthCell(it, date, date.toEpochDay() in entryEpochDays)
    },
)
