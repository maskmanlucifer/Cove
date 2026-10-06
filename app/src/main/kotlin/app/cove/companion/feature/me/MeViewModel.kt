package app.cove.companion.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.feature.onboarding.saveWakeTime
import app.cove.companion.data.sync.ConflictDescriber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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

    /** Sync row text, refreshed every 30 s so "2 min ago" keeps moving. */
    val sync: StateFlow<SyncUi> = combine(
        c.sync.authState, c.sync.status, c.sync.conflicts,
        flow { while (true) { emit(Unit); delay(30_000) } },
    ) { auth, status, conflicts, _ -> syncUi(auth, status, conflicts.size, c.clock.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUi("", false, false, 0))

    /** Title of the oldest unresolved conflict, for the banner. */
    val conflictTitle: StateFlow<String?> = c.sync.conflicts
        .map { list -> list.firstOrNull()?.let { ConflictDescriber.describe(it).title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun syncNow() = c.sync.requestSync()

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }

    fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { c.settings.update(change) }
    }

    fun setWake(minutes: Int) {
        viewModelScope.launch { saveWakeTime(c, minutes) }
    }
}
