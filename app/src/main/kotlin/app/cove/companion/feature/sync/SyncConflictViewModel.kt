package app.cove.companion.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.sync.ConflictDescriber
import app.cove.companion.data.sync.ConflictSummary
import app.cove.companion.data.sync.Resolution
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The oldest unresolved conflict with its screen-ready description. */
data class ConflictUi(val entity: SyncConflictEntity, val summary: ConflictSummary)

/** Drives the "Which one should stay?" sheet; null state means still loading. */
class SyncConflictViewModel(private val c: AppContainer) : ViewModel() {
    /** `Some(null)`-style: null while loading, then the current conflict or an empty marker. */
    val state: StateFlow<ConflictState> = c.sync.conflicts
        .map { list -> list.firstOrNull()?.let { ConflictState.Open(ConflictUi(it, ConflictDescriber.describe(it)), list.size) } ?: ConflictState.None }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConflictState.Loading)

    fun resolve(conflict: SyncConflictEntity, how: Resolution) {
        viewModelScope.launch { c.conflictResolver.resolve(conflict, how) }
    }
}

/** What the conflict screen has to show. */
sealed interface ConflictState {
    data object Loading : ConflictState
    data object None : ConflictState
    data class Open(val ui: ConflictUi, val total: Int) : ConflictState
}
