package app.cove.companion.feature.money

import app.cove.companion.core.clock
import app.cove.companion.core.rupees
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.ExpenseEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/** How a day in the daily strip is drawn. */
enum class BarKind { Spent, Quiet, Future }

/** One day of the month in the strip; [fraction] is its spend relative to the busiest day. */
data class DayBar(val day: Int, val kind: BarKind, val fraction: Float)

/** Expenses of one day, newest first. */
data class DayGroup(val label: String, val items: List<ExpenseEntity>)

/** Pure month arithmetic for the Money screens. All money is paise. */
object MoneyMath {
    private val shortDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

    /** Rounds to whole rupees for display, e.g. 1157950 -> "₹11,580". */
    fun wholeRupees(paise: Long): String = rupees((paise + 50) / 100 * 100)

    fun spentOf(expenses: List<ExpenseEntity>): Long = expenses.filter { it.kind == "spent" }.sumOf { it.amountPaise }

    /** Days left in the month after [today]. */
    fun daysToGo(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth

    /** Rough daily allowance for the rest of the month, in whole rupees; null when nothing is left. */
    fun perDay(leftPaise: Long, daysToGo: Int): Long? =
        if (leftPaise <= 0) null else (leftPaise / 100.0 / max(daysToGo, 1)).roundToLong() * 100

    /** Fill of a progress bar, 0..1. */
    fun progress(spent: Long, budget: Long): Float =
        if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)

    fun isOver(spent: Long, budget: Long) = budget > 0 && spent > budget

    /** "₹210 over · no rush", or null while within budget. */
    fun overNote(spent: Long, budget: Long): String? =
        if (isOver(spent, budget)) "${wholeRupees(spent - budget)} over · no rush" else null

    /** Compact variant shown beside an amount: " · ₹210 over, no rush". */
    fun overInline(spent: Long, budget: Long): String? =
        if (isOver(spent, budget)) " · ${wholeRupees(spent - budget)} over, no rush" else null

    /** What last month's unspent budget adds to this month when "carry over" is on. */
    fun carriedOver(prevBudget: Long, prevSpent: Long, enabled: Boolean): Long =
        if (enabled) max(prevBudget - prevSpent, 0) else 0

    /** True when a new expense moves spending across the 80% line of a budget that asked to be told. */
    fun crossedAlert(before: Long, after: Long, budget: Long, enabled: Boolean): Boolean =
        enabled && budget > 0 && before * 100 < budget * 80 && after * 100 >= budget * 80

    fun alertMessage(name: String, spent: Long, budget: Long): String =
        if (isOver(spent, budget)) "$name is past its budget · no rush"
        else "$name is at 80% · ${wholeRupees(budget - spent)} left"

    /** One bar per day of [today]'s month: spend so far, quiet days, and days still to come. */
    fun dailyBars(expenses: List<ExpenseEntity>, today: LocalDate): List<DayBar> {
        val perDay = expenses.filter { it.kind == "spent" }.groupBy { it.spentAt.toLocalDate() }
        val peak = perDay.filterKeys { it.month == today.month && it.year == today.year }.values
            .maxOfOrNull { list -> list.sumOf { it.amountPaise } } ?: 0L
        return (1..today.lengthOfMonth()).map { day ->
            val spent = perDay[today.withDayOfMonth(day)]?.sumOf { it.amountPaise } ?: 0L
            when {
                day > today.dayOfMonth -> DayBar(day, BarKind.Future, 0f)
                spent > 0 && peak > 0 -> DayBar(day, BarKind.Spent, spent.toFloat() / peak)
                else -> DayBar(day, BarKind.Quiet, 0f)
            }
        }
    }

    /** "Today", "Yesterday" or "Sat, 3 Oct". */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(shortDay)
    }

    fun groupByDay(expenses: List<ExpenseEntity>, today: LocalDate): List<DayGroup> =
        expenses.sortedByDescending { it.spentAt }
            .groupBy { it.spentAt.toLocalDate() }
            .map { (day, items) -> DayGroup(dayLabel(day, today), items) }

    /** "6:40 pm". */
    fun timeText(millis: Long): String = millis.toLocalDateTime().clock().let { it.digits + it.suffix }

    /** "Today, 6:40 pm" / "Yesterday, 9:05 am" / "Sat, 3 Oct, 9:05 am". */
    fun whenText(millis: Long, today: LocalDate): String =
        "${dayLabel(millis.toLocalDate(), today)}, ${timeText(millis)}"

    /** Second line of a transaction row: "UPI · 6:40 pm" or "By voice · 1:12 pm". */
    fun methodLine(e: ExpenseEntity): String =
        (if (e.source == "voice") "By voice" else e.paidWith) + " · " + timeText(e.spentAt)

    /** "Food so far: ₹7,340 of ₹9,000." */
    fun soFarLine(name: String, spent: Long, budget: Long): String =
        if (budget > 0) "$name so far: ${wholeRupees(spent)} of ${wholeRupees(budget)}." else "$name so far: ${wholeRupees(spent)}."
}
