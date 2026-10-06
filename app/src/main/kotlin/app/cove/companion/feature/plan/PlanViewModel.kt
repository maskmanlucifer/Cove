package app.cove.companion.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Plan tab draws. */
data class PlanState(
    val timeline: List<TimelineRow> = emptyList(),
    val groups: List<CategoryGroup> = emptyList(),
    /** Every live to-do, including uncategorised ones that only show on the schedule. */
    val todos: List<TodoEntity> = emptyList(),
    /** Today's events, so a tapped schedule line can open its editor. */
    val events: List<EventEntity> = emptyList(),
    /** Epoch millis of "now", used for relative labels. */
    val now: Long = 0,
)

/** A reversible change shown in the Undo bar for six seconds. */
data class UndoNotice(val id: Long, val message: String, val restore: () -> Unit)

/** Builds the schedule and to-do lists and applies every edit made on the Plan tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val c: AppContainer) : ViewModel() {
    private val ticker = flow {
        while (true) {
            emit(c.clock.now())
            delay(30_000)
        }
    }

    private val events = ticker.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { day ->
        combine(c.plan.eventsOn(day), c.plan.repeatingEvents(day)) { once, repeating -> (once + repeating).distinctBy { it.id } }
    }

    val state: StateFlow<PlanState> = combine(
        ticker, events, c.plan.alarms, c.todos.todos, c.todos.categories,
    ) { now, events, alarms, todos, categories ->
        val at = now.toLocalDateTime()
        val items = scheduleItems(at.toLocalDate(), events, alarms, todos)
        PlanState(buildTimeline(items, at.hour * 60 + at.minute), groupTodos(categories, todos, now), todos.filter { it.deletedAt == null }, events, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanState())

    private val _undo = MutableStateFlow<UndoNotice?>(null)

    /** The change that can currently be undone, if any. */
    val undo: StateFlow<UndoNotice?> = _undo

    private var undoSeq = 0L

    private fun offerUndo(message: String, restore: suspend () -> Unit) {
        _undo.value = UndoNotice(++undoSeq, message) { viewModelScope.launch { restore() } }
    }

    /** Hides the Undo bar if it still shows notice [id]. */
    fun expireUndo(id: Long) {
        if (_undo.value?.id == id) _undo.value = null
    }

    /** Runs the pending undo and hides the bar. */
    fun undoLast() {
        _undo.value?.restore?.invoke()
        _undo.value = null
    }

    /** Ticks or un-ticks [todo], saving [edited] (its unsaved edits) on the way; ticking offers Undo. */
    fun setDone(todo: TodoEntity, done: Boolean, edited: TodoEntity = todo) = viewModelScope.launch {
        c.todos.save(edited.copy(done = done, doneAt = if (done) c.clock.now() else null))
        if (done) offerUndo("Finished “${todo.title}”") { c.todos.save(todo) }
    }

    /** Soft-deletes [todo] with Undo. */
    fun delete(todo: TodoEntity) = viewModelScope.launch {
        c.todos.delete(todo.id)
        offerUndo("Deleted “${todo.title}”") { c.todos.save(todo.copy(deletedAt = null)) }
    }

    fun save(todo: TodoEntity) = viewModelScope.launch { c.todos.save(todo) }

    /** Appends a new open to-do to [categoryId]. */
    fun addTodo(title: String, categoryId: String?, dueAt: Long? = null, remind: Boolean = false) = viewModelScope.launch {
        val clean = title.trim()
        if (clean.isEmpty()) return@launch
        val next = state.value.groups.firstOrNull { it.category.id == categoryId }?.open?.maxOfOrNull { it.sort }?.plus(1) ?: 0
        c.todos.save(TodoEntity(newId(), categoryId, clean, dueAt, remind, sort = next))
    }

    /** Drops [id] at [index] among the open to-dos of [categoryId]. */
    fun move(id: String, categoryId: String?, index: Int) = viewModelScope.launch {
        val all = state.value.groups.flatMap { it.open + it.doneToday + it.doneEarlier }
        c.todos.saveAll(moveTodo(all, id, categoryId, index))
    }

    fun addEvent(event: EventEntity) = viewModelScope.launch { c.plan.saveEvent(event) }

    /** Soft-deletes [event] with Undo. */
    fun deleteEvent(event: EventEntity) = viewModelScope.launch {
        c.plan.saveEvent(event.copy(deletedAt = c.clock.now()))
        offerUndo("Deleted “${event.title}”") { c.plan.saveEvent(event.copy(deletedAt = null)) }
    }

    /** Creates a category at the end and returns its id. */
    fun addCategory(name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isEmpty()) return@launch
        val last = state.value.groups.maxOfOrNull { it.category.sort }?.plus(1) ?: 0
        c.todos.saveCategory(TodoCategoryEntity(newId(), clean, last))
    }

    fun renameCategory(category: TodoCategoryEntity, name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean != category.name) c.todos.saveCategory(category.copy(name = clean))
    }

    /** Saves the order of [ordered] categories as their `sort`. */
    fun reorderCategories(ordered: List<TodoCategoryEntity>) = viewModelScope.launch {
        ordered.forEachIndexed { i, cat -> if (cat.sort != i) c.todos.saveCategory(cat.copy(sort = i)) }
    }

    /** Deletes [category]; its to-dos (open and done) move to [moveTo] first. */
    fun deleteCategory(category: TodoCategoryEntity, moveTo: String?) = viewModelScope.launch {
        val group = state.value.groups.firstOrNull { it.category.id == category.id }
        val moved = group?.let { it.open + it.doneToday + it.doneEarlier }.orEmpty()
        if (moveTo != null) {
            val tail = state.value.groups.firstOrNull { it.category.id == moveTo }?.open?.size ?: 0
            c.todos.saveAll(moved.filter { !it.done }.mapIndexed { i, t -> t.copy(categoryId = moveTo, sort = tail + i) })
            c.todos.saveAll(moved.filter { it.done }.map { it.copy(categoryId = moveTo) })
        } else {
            moved.forEach { c.todos.delete(it.id) }
        }
        c.todos.saveCategory(category.copy(deletedAt = c.clock.now()))
    }
}
