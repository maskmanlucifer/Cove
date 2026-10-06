package app.cove.companion.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The single thing One-thing mode shows; [title] is null when nothing is waiting. */
data class OneThingState(val id: String? = null, val title: String? = null, val loaded: Boolean = false)

/**
 * Picks the one open to-do to show: soonest due first, then list order, skipping ones the user
 * pushed back with "Not now" until they have all been skipped once.
 */
fun pickOneThing(todos: List<TodoEntity>, skipped: List<String>): TodoEntity? {
    val open = todos
        .filter { !it.done && it.deletedAt == null }
        .sortedWith(compareBy({ it.dueAt ?: Long.MAX_VALUE }, { it.sort }))
    return open.firstOrNull { it.id !in skipped } ?: open.firstOrNull()
}

/** Drives [OneThingContent]: current item, done, not now and exit. */
class OneThingViewModel(private val c: AppContainer) : ViewModel() {
    private val skipped = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<OneThingState> = combine(c.todos.todos, skipped) { todos, skip ->
        val open = todos.count { !it.done && it.deletedAt == null }
        val pick = pickOneThing(todos, skip.takeIf { it.size < open } ?: emptyList())
        OneThingState(pick?.id, pick?.title, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OneThingState())

    fun done() {
        val id = state.value.id ?: return
        viewModelScope.launch { c.todos.setDone(id, true) }
    }

    fun notNow() {
        val id = state.value.id ?: return
        skipped.value = skipped.value + id
    }

    /** Turns the mode off so Today shows its normal layout again. */
    fun exit(done: () -> Unit) {
        viewModelScope.launch {
            c.settings.update { it.copy(oneThingMode = false) }
            done()
        }
    }
}
