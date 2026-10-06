package app.cove.companion.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.DayPhase
import app.cove.companion.core.dayPhase
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.entity.EventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** The single "Next" card at the top of Today. */
data class NextItem(
    val minutes: Int,
    val title: String,
    val detail: String?,
    val inMinutes: Long,
    val event: EventEntity? = null,
    val windDown: Boolean = false,
)

data class TodoRow(val id: String, val title: String, val time: LocalDateTime?, val done: Boolean, val isNew: Boolean = false)

data class TodayState(
    val name: String = "",
    val phase: DayPhase = DayPhase.Morning,
    val date: LocalDate = LocalDate.now(),
    val next: NextItem? = null,
    val todos: List<TodoRow> = emptyList(),
    val spentTodayPaise: Long = 0,
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    val doneCount: Int = 0,
    val remainingBeforeNoon: Boolean = false,
)

private data class Aux(
    val expenses: List<app.cove.companion.data.local.entity.ExpenseEntity>,
    val habits: List<app.cove.companion.data.local.entity.HabitEntity>,
    val logs: List<app.cove.companion.data.local.entity.HabitLogEntity>,
    val newIds: Set<String>,
)

/** Builds [TodayState] from settings, events, alarms, to-dos, spending and habits. */
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val dayStart = today.startOfDayMillis()
    private val dayEnd = today.plusDays(1).startOfDayMillis() - 1

    val state: StateFlow<TodayState> = combine(
        c.settings.settings,
        c.plan.eventsOn(today),
        c.plan.alarms,
        c.todos.todos,
        combine(c.money.expenses(dayStart, dayEnd), c.habits.habits, c.habits.logs(today, today), c.voice.newTodos.ids) { e, h, l, n ->
            Aux(e, h, l, n)
        },
    ) { settings, events, alarms, todos, (expenses, habits, logs, newIds) ->
        val now = c.clock.now().toLocalDateTime()
        val phase = dayPhase(now.hour)
        val nowMin = now.hour * 60 + now.minute
        val upcoming = events.firstOrNull { it.startAt.toLocalDateTime() >= now }
        val bedtime = alarms.firstOrNull { it.kind == "bedtime" && it.enabled }
        val wake = alarms.firstOrNull { it.kind == "wake" && it.enabled }
        val next = when {
            phase == DayPhase.Evening && bedtime != null -> NextItem(
                bedtime.minutes, "Wind down",
                wake?.let { "Screen dims · alarm ${app.cove.companion.core.clockText(it.minutes).digits}" },
                (bedtime.minutes - nowMin).toLong(), windDown = true,
            )
            upcoming != null -> {
                val t = upcoming.startAt.toLocalDateTime()
                NextItem(
                    t.hour * 60 + t.minute, upcoming.title,
                    listOfNotNull(upcoming.place, upcoming.notes?.takeIf { upcoming.place == null }).joinToString(" · ").ifEmpty { null },
                    java.time.Duration.between(now, t).toMinutes(), upcoming,
                )
            }
            else -> null
        }
        val rows = todos
            .filter { it.dueAt == null || it.dueAt in dayStart..dayEnd || it.done }
            .sortedByDescending { it.id in newIds }
            .take(3)
            .map { TodoRow(it.id, it.title, it.dueAt?.toLocalDateTime(), it.done, isNew = it.id in newIds) }
        val shown = habits.filter { it.showOnToday }
        TodayState(
            name = settings.displayName,
            phase = phase,
            date = today,
            next = next,
            todos = rows,
            spentTodayPaise = expenses.filter { it.kind == "spent" }.sumOf { it.amountPaise },
            habitsDone = shown.count { h -> logs.any { it.habitId == h.id } },
            habitsTotal = shown.size,
            doneCount = todos.count { it.done },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun toggle(id: String, done: Boolean) {
        viewModelScope.launch { c.todos.setDone(id, done) }
    }

    /** The "New" tag fades once the user has seen it. */
    fun markNewSeen() {
        c.voice.newTodos.markSeen(state.value.todos.filter { it.isNew }.map { it.id })
    }

    fun setMood(mood: String) {
        viewModelScope.launch {
            val entry = c.journal.newEntry().copy(mood = mood)
            c.journal.save(entry)
        }
    }
}
