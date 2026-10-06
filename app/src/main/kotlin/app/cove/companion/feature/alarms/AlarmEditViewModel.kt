package app.cove.companion.feature.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Draft of the alarm being edited. [id] is an alarm id, "new", or "bedtime" (new bedtime alarm).
 * [draft] stays null until loaded.
 */
class AlarmEditViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val _draft = MutableStateFlow<AlarmEntity?>(null)
    val draft: StateFlow<AlarmEntity?> = _draft

    /** True when the alarm already exists, so Delete is offered. */
    var existing = false
        private set

    init {
        viewModelScope.launch {
            val found = if (id == "new" || id == "bedtime") null else c.plan.alarm(id)
            existing = found != null
            _draft.value = found ?: when (id) {
                "bedtime" -> AlarmEntity(newId(), "Bedtime", 22 * 60 + 30, AlarmDays.ALL, kind = "bedtime", gentleRise = false)
                else -> AlarmEntity(newId(), "Alarm", 7 * 60, AlarmDays.WEEKDAYS)
            }
        }
    }

    fun edit(change: (AlarmEntity) -> AlarmEntity) = _draft.update { it?.let(change) }

    /** Saves the draft; editing an alarm switches it on. */
    suspend fun save() {
        _draft.value?.let { c.plan.saveAlarm(it.copy(enabled = true)) }
    }

    suspend fun delete() {
        c.plan.deleteAlarm(id.takeIf { existing } ?: return)
    }
}
