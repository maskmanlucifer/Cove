package app.cove.companion.feature.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.DayPhase
import app.cove.companion.core.dayPhase
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.core.clockText
import app.cove.companion.feature.alarms.nextFireMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
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
    val oneThingMode: Boolean = false,
)

private data class Aux(
    val expenses: List<app.cove.companion.data.local.entity.ExpenseEntity>,
    val habits: List<app.cove.companion.data.local.entity.HabitEntity>,
    val logs: List<app.cove.companion.data.local.entity.HabitLogEntity>,
    val newIds: Set<String>,
    val hiddenUntil: Long,
)

/**
 * When the Next card comes back after "Later" or "Start now" (epoch millis; 0 = visible).
 * Kept in local preferences so it survives restarts; it is per device and never synced.
 */
class NextCardMemory(context: Context) {
    private val prefs = context.getSharedPreferences("today_card", Context.MODE_PRIVATE)
    private val _hiddenUntil = MutableStateFlow(prefs.getLong(KEY, 0L))
    val hiddenUntil: StateFlow<Long> = _hiddenUntil

    /** Hides the card until [millis]. */
    fun hideUntil(millis: Long) {
        prefs.edit().putLong(KEY, millis).apply()
        _hiddenUntil.value = millis
    }

    private companion object {
        const val KEY = "hidden_until"
    }
}

/** Builds [TodayState] from settings, events, alarms, to-dos, spending and habits. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val dayStart = today.startOfDayMillis()
    private val dayEnd = today.plusDays(1).startOfDayMillis() - 1

    val state: StateFlow<TodayState> = combine(
        c.settings.settings,
        c.plan.eventsOn(today),
        c.plan.alarms,
        c.todos.todos,
        combine(c.money.expenses(dayStart, dayEnd), c.habits.habits, c.habits.logs(today, today), c.voice.newTodos.ids, hiddenFlow()) { e, h, l, n, hidden ->
            Aux(e, h, l, n, hidden)
        },
    ) { settings, events, alarms, todos, (expenses, habits, logs, newIds, hiddenUntil) ->
        val now = c.clock.now().toLocalDateTime()
        val phase = dayPhase(now.hour)
        val nowMin = now.hour * 60 + now.minute
        val upcoming = events.firstOrNull { it.startAt.toLocalDateTime() >= now }
        val bedtime = alarms.firstOrNull { it.kind == "bedtime" && it.enabled }
        val wake = alarms.firstOrNull { it.kind == "wake" && it.enabled }
        val next = if (cardHidden(c.clock.now(), hiddenUntil)) null else when {
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
            oneThingMode = oneThingActive(settings, c.clock.now()),
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

    /** Emits the hide deadline, then 0 once it has passed so the card reappears without a refresh. */
    private fun hiddenFlow(): Flow<Long> = c.nextCard.hiddenUntil.flatMapLatest { until ->
        flow {
            emit(until)
            val wait = until - c.clock.now()
            if (wait > 0) {
                delay(wait)
                emit(0L)
            }
        }
    }

    init {
        viewModelScope.launch {
            c.settings.settings.collectLatest { s ->
                if (s.oneThingMode && s.oneThingUntil > 0) {
                    delay((s.oneThingUntil - c.clock.now()).coerceAtLeast(0))
                    c.settings.update { it.copy(oneThingMode = false, oneThingUntil = 0) }
                }
            }
        }
    }

    /** "Later": hides the Next card for [CARD_SNOOZE_MINUTES] minutes. */
    fun snoozeCard() {
        c.nextCard.hideUntil(snoozeUntil(c.clock.now()))
    }

    /**
     * Wind-down "Start now": hides the card for tonight and turns One-thing mode on until the wake alarm.
     * Returns the wake alarm's time text for the spoken line, or null when there is none.
     */
    suspend fun startWindDown(): String? {
        val now = c.clock.now()
        val wake = c.plan.alarms.first().firstOrNull { it.kind == "wake" && it.enabled && it.deletedAt == null }
        c.nextCard.hideUntil(tonightEnd(now))
        val until = wake?.let { nextFireMillis(it.minutes, it.daysMask, now) } ?: 0L
        c.settings.update { it.copy(oneThingMode = true, oneThingUntil = until) }
        return wake?.let { clockText(it.minutes).let { t -> t.digits + t.suffix } }
    }

    fun saveEvent(event: EventEntity) {
        viewModelScope.launch { c.plan.saveEvent(event) }
    }

    /** Soft-deletes [event]; [restoreEvent] brings it back (Undo). */
    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { c.plan.saveEvent(event.copy(deletedAt = c.clock.now())) }
    }

    fun restoreEvent(event: EventEntity) {
        viewModelScope.launch { c.plan.saveEvent(event.copy(deletedAt = null)) }
    }

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
