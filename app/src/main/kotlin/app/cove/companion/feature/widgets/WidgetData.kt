package app.cove.companion.feature.widgets

import app.cove.companion.AppContainer
import app.cove.companion.core.DayPhase
import app.cove.companion.core.clockText
import app.cove.companion.core.dayPhase
import app.cove.companion.core.inText
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.feature.nudges.NudgeMath
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** "Next" card content: [inText] ("in 25 min"), the clock split as in the design, and the title. */
data class NextCard(val inText: String, val digits: String, val suffix: String, val title: String)

/** One row of the tasks widget. */
data class TaskRow(val id: String, val title: String, val done: Boolean)

/** Everything the widgets draw, computed from the database in one cheap read. */
data class WidgetSnapshot(
    val next: NextCard?,
    val tasks: List<TaskRow>,
    val tasksLeft: Int,
    val spentTodayPaise: Long,
    val leftThisMonthPaise: Long?,
)

/** Builds [WidgetSnapshot]s; pure apart from [load], so it can be tested. Widgets never run Nano or heavy work. */
object WidgetData {
    /** Reads the current state of the database once. */
    suspend fun load(c: AppContainer): WidgetSnapshot {
        val now = c.clock.now()
        val day = now.toLocalDate()
        val monthStart = day.withDayOfMonth(1).startOfDayMillis()
        val monthEnd = day.plusMonths(1).withDayOfMonth(1).startOfDayMillis() - 1
        return build(
            now,
            c.todos.todos.first(),
            c.plan.upcomingEvents(30).first() + c.plan.repeatingEvents(day.plusYears(100)).first(),
            c.plan.alarms.first(),
            c.money.expenses(monthStart, monthEnd).first(),
            c.money.categories.first(),
        )
    }

    fun build(
        now: Long,
        todos: List<TodoEntity>,
        events: List<EventEntity>,
        alarms: List<AlarmEntity>,
        monthExpenses: List<ExpenseEntity>,
        categories: List<ExpenseCategoryEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetSnapshot {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = monthExpenses.filter { it.kind == "spent" }
        val budget = categories.filter { it.kind == "spending" }.sumOf { it.budgetPaise }
        val relevant = todos.filter { it.deletedAt == null && (it.dueAt == null || it.dueAt in dayStart until dayEnd) }
        val open = relevant.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.dueAt })
        val doneToday = relevant.filter { it.done && (it.doneAt ?: 0) >= dayStart }
        return WidgetSnapshot(
            next = nextCard(now, today.atStartOfDay(zone).toInstant().toEpochMilli(), events, open, alarms, zone),
            tasks = (open + doneToday).map { TaskRow(it.id, it.title, it.done) },
            tasksLeft = open.size,
            spentTodayPaise = spent.filter { it.spentAt in dayStart until dayEnd }.sumOf { it.amountPaise },
            leftThisMonthPaise = if (budget > 0) budget - spent.sumOf { it.amountPaise } else null,
        )
    }

    private fun nextCard(now: Long, dayStart: Long, events: List<EventEntity>, open: List<TodoEntity>, alarms: List<AlarmEntity>, zone: ZoneId): NextCard? {
        val local = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val bedtime = alarms.firstOrNull { it.kind == "bedtime" && it.enabled && it.deletedAt == null }
        if (dayPhase(local.hour) == DayPhase.Evening && bedtime != null) {
            val c = clockText(bedtime.minutes)
            return NextCard(inText((bedtime.minutes - local.hour * 60 - local.minute).toLong()), c.digits, c.suffix, "Start winding down")
        }
        val endOfDay = dayStart + 24 * 3_600_000L
        val event = events.filter { it.deletedAt == null }.mapNotNull { e ->
            NudgeMath.nextOccurrence(e.startAt, e.repeat, 0, now - 1, zone)?.takeIf { it < endOfDay }?.let { it to e.title }
        }
        val todo = open.filter { it.dueAt != null && it.dueAt >= now }.map { it.dueAt!! to it.title }
        val (at, title) = (event + todo).minByOrNull { it.first } ?: return null
        val t = at.toLocalDateTime()
        val c = clockText(t.hour * 60 + t.minute)
        return NextCard(inText((at - now + 59_999) / 60_000), c.digits, c.suffix, title)
    }
}
