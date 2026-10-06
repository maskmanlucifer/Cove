package app.cove.companion.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Holds the wake time being picked and writes onboarding results. */
class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _wake = MutableStateFlow(SETTINGS_DEFAULT)
    val wake: StateFlow<Int> = _wake

    init {
        viewModelScope.launch {
            // A wake alarm that arrived through sync wins over the default; show its time, not 6:30.
            val synced = c.plan.alarms.first().firstOrNull { it.kind == "wake" && it.deletedAt == null }
            _wake.value = snapWake(synced?.minutes ?: c.settings.settings.first().wakeMinutes)
        }
    }

    fun setWake(minutes: Int) {
        _wake.value = minutes
    }

    /** Saves the wake time (see [saveWakeTime]), then calls [done]. */
    fun saveWake(done: () -> Unit) {
        viewModelScope.launch {
            val existing = c.plan.alarms.first().firstOrNull { it.kind == "wake" && it.deletedAt == null }
            if (existing == null || snapWake(existing.minutes) != _wake.value) saveWakeTime(c, _wake.value) else keepExistingWake(c)
            done()
        }
    }

    /** Marks onboarding complete, then calls [done]. */
    fun finish(done: () -> Unit) {
        viewModelScope.launch {
            c.settings.update { it.copy(onboarded = true) }
            done()
        }
    }

    private companion object {
        const val SETTINGS_DEFAULT = 6 * 60 + 30
    }
}

/** Onboarding found a wake alarm already (synced from another phone): keep it as is and mirror its time into settings. */
suspend fun keepExistingWake(c: AppContainer) {
    val existing = c.plan.alarms.first().firstOrNull { it.kind == "wake" && it.deletedAt == null } ?: return
    c.settings.update { it.copy(wakeMinutes = existing.minutes) }
}

/** Stores [minutes] as the wake time and creates or updates the default every-day "Wake up" alarm. */
suspend fun saveWakeTime(c: AppContainer, minutes: Int) {
    c.settings.update { it.copy(wakeMinutes = minutes) }
    val existing = c.plan.alarms.first().firstOrNull { it.kind == "wake" && it.deletedAt == null }
    c.plan.saveAlarm(
        existing?.copy(minutes = minutes, daysMask = EveryDayMask, enabled = true)
            ?: AlarmEntity(newId(), "Wake up", minutes, EveryDayMask, kind = "wake"),
    )
}
