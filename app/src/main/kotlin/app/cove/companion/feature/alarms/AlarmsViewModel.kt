package app.cove.companion.feature.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Alarms list: wake alarms, the bedtime alarm and the "Next in ..." caption. */
data class AlarmsState(
    val alarms: List<AlarmEntity> = emptyList(),
    val bedtime: AlarmEntity? = null,
    val nextText: String? = null,
)

class AlarmsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<AlarmsState> = c.plan.alarms.map { all ->
        val now = c.clock.now()
        val next = all.filter { it.enabled && it.kind != "bedtime" }
            .minOfOrNull { nextFireMillis(it.minutes, it.daysMask, now) }
        AlarmsState(
            alarms = all.filter { it.kind != "bedtime" },
            bedtime = all.firstOrNull { it.kind == "bedtime" },
            nextText = next?.let { "Next in ${untilText(it - now)}" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmsState())

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch { c.plan.saveAlarm(alarm.copy(enabled = enabled)) }
    }
}
