package app.cove.companion.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.feature.onboarding.saveWakeTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reads and writes the settings shown on the Me tab; every change persists immediately. */
class MeViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<SettingsEntity?> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Enabled alarms, earliest first. */
    val alarms: StateFlow<List<AlarmEntity>> = c.plan.alarms
        .map { list -> list.filter { it.deletedAt == null && it.enabled }.sortedBy { it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { c.settings.update(change) }
    }

    fun setWake(minutes: Int) {
        viewModelScope.launch { saveWakeTime(c, minutes) }
    }
}
