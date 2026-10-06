package app.cove.companion.feature.habits

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a habit appears on Today. */
enum class ShowMode(val label: String) {
    AfterWakeUp("From wake-up"),
    AllDay("All day"),
    Never("Not on Today"),
}

/** Cadence names as stored in [HabitEntity.cadence], in the order of the "How often" control. */
val Cadences = listOf("daily", "days")

data class HabitEditState(
    val isNew: Boolean = true,
    val cadence: Int = 1,
    val daysMask: Int = 0b0010101,
    val remindMinutes: Int? = 7 * 60 + 30,
    val show: ShowMode = ShowMode.AfterWakeUp,
)

/** Create or edit one habit. New habits start as the design shows: some days (M W F), a 7:30 am reminder, after wake-up. */
class HabitEditViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val name = TextFieldState()
    private val _state = MutableStateFlow(HabitEditState(isNew = id == "new"))
    val state: StateFlow<HabitEditState> = _state.asStateFlow()
    private var existing: HabitEntity? = null

    init {
        if (id != "new") viewModelScope.launch {
            val h = c.habits.get(id) ?: return@launch
            existing = h
            name.setTextAndPlaceCursorAtEnd(h.name)
            _state.value = HabitEditState(
                isNew = false,
                cadence = if (h.cadence == "daily") 0 else 1,
                daysMask = h.daysMask,
                remindMinutes = h.remindMinutes,
                show = when {
                    !h.showOnToday -> ShowMode.Never
                    h.afterWakeUp -> ShowMode.AfterWakeUp
                    else -> ShowMode.AllDay
                },
            )
        }
    }

    fun setCadence(index: Int) = _state.update { s -> s.copy(cadence = index, daysMask = if (s.daysMask == 0) 0b0000001 else s.daysMask) }

    /** Toggles [bit] for "Some days". Keeps at least one day. */
    fun toggleDay(bit: Int) = _state.update { s ->
        val mask = s.daysMask xor bit
        if (mask == 0) s else s.copy(daysMask = mask)
    }

    fun setReminder(minutes: Int?) = _state.update { it.copy(remindMinutes = minutes) }

    fun setShow(mode: ShowMode) = _state.update { it.copy(show = mode) }

    /** Id a new habit is saved under, fixed for this screen so a repeated save rewrites one row. */
    private val newHabitId = newId()

    /** Saves the habit; false when the name is blank. */
    suspend fun save(): Boolean {
        val n = name.text.toString().trim()
        if (n.isEmpty()) return false
        val s = _state.value
        val base = existing ?: HabitEntity(newHabitId, n, sort = (c.habits.habits.first().maxOfOrNull { it.sort } ?: -1) + 1)
        c.habits.save(
            base.copy(
                name = n,
                cadence = Cadences[s.cadence],
                daysMask = if (s.cadence == 0) 127 else s.daysMask,
                remindMinutes = s.remindMinutes,
                afterWakeUp = s.show == ShowMode.AfterWakeUp,
                showOnToday = s.show != ShowMode.Never,
            ),
        )
        return true
    }

    suspend fun delete() = c.habits.delete(id)
}
