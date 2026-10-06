package app.cove.companion.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One habit row: its last 7 days (oldest first, today last) and the "n of 7 days" text. */
data class HabitRow(val id: String, val name: String, val days: List<Boolean>, val summary: String)

data class HabitsState(val rows: List<HabitRow> = emptyList(), val loaded: Boolean = false)

/** Last-7-days view of every habit, and the today toggle. */
class HabitsViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()

    val state: StateFlow<HabitsState> = combine(c.habits.habits, c.habits.logs(today.minusDays(6), today)) { habits, logs ->
        HabitsState(
            habits.map { h ->
                val days = lastSevenDays(today, logs.filter { it.habitId == h.id }.map { it.day }.toSet())
                HabitRow(h.id, h.name, days, countText(days))
            },
            loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsState())

    /** Ticks or un-ticks today for [habitId]. */
    fun toggleToday(habitId: String) {
        viewModelScope.launch { c.habits.toggle(habitId, today) }
    }
}
