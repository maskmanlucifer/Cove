package app.cove.companion.feature.suggest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.data.repo.pendingIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The active suggestion for Today, its card reasons and whether the "Why" sheet is open. */
data class SuggestionState(
    val decision: DecisionEntity? = null,
    val detail: DecisionDetail = DecisionDetail(emptyList()),
    val whyOpen: Boolean = false,
    /** Ids of to-dos with changes that have not synced yet. */
    val pendingTodoIds: Set<String> = emptySet(),
)

/** Drives the suggestion card and the "will sync" markers on Today. */
class SuggestionViewModel(private val c: AppContainer) : ViewModel() {
    private val whyOpen = MutableStateFlow(false)
    private val engine get() = c.decisions

    val state: StateFlow<SuggestionState> = combine(c.assistant.activeDecision, whyOpen, c.database.pendingIds("todos")) { d, why, pending ->
        val shown = d?.takeIf { engine.visible(it) }
        SuggestionState(shown, shown?.let { DecisionDetail.decode(it.reasons) } ?: DecisionDetail(emptyList()), why && shown != null, pending.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SuggestionState())

    init {
        viewModelScope.launch { c.plan.alarms.collect { engine.refresh() } }
    }

    fun accept() = act { engine.accept(it) }

    fun keep() = act { engine.keep(it) }

    fun mute() = act {
        whyOpen.value = false
        engine.mute(it)
    }

    fun showWhy(open: Boolean) {
        whyOpen.value = open
    }

    private fun act(block: suspend (DecisionEntity) -> Unit) {
        val d = state.value.decision ?: return
        viewModelScope.launch { block(d) }
    }
}
