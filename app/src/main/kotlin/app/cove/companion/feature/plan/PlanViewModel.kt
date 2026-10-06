package app.cove.companion.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import java.time.LocalDate
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
    /** The day the Schedule shows. */
    val day: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
)

/** A reversible change shown in the Undo bar for six seconds. */
data class UndoNotice(val id: Long, val message: String, val action: String = "Undo", val restore: () -> Unit)

/** Builds the schedule and to-do lists and applies every edit made on the Plan tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val c: AppContainer) : ViewModel() {
    private val ticker = flow {
        while (true) {
            emit(c.clock.now())
            delay(30_000)
        }
    }

    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Schedule day; null follows today. */
    private val shownDay = combine(ticker.map { it.toLocalDate() }.distinctUntilChanged(), selectedDay) { today, sel -> sel?.takeIf { it != today } }
        .distinctUntilChanged()

    /** Moves the Schedule by [delta] days. */
    fun shiftDay(delta: Long) {
        val today = c.clock.now().toLocalDate()
        selectedDay.value = ((selectedDay.value ?: today).plusDays(delta)).takeIf { it != today }
    }

    /** Shows [day] on the Schedule. */
    fun showDay(day: LocalDate) {
        selectedDay.value = day.takeIf { it != c.clock.now().toLocalDate() }
    }

    /** Back to today. */
    fun showToday() {
        selectedDay.value = null
    }

    private val events = combine(ticker.map { it.toLocalDate() }.distinctUntilChanged(), shownDay) { today, sel -> sel ?: today }.distinctUntilChanged().flatMapLatest { day ->
        combine(c.plan.eventsOn(day), c.plan.repeatingEvents(day)) { once, repeating -> (once + repeating).distinctBy { it.id } }
    }

    val state: StateFlow<PlanState> = combine(
        ticker, events, combine(c.plan.alarms, shownDay) { a, d -> a to d }, c.todos.todos, c.todos.categories,
    ) { now, events, (alarms, picked), todos, categories ->
        val at = now.toLocalDateTime()
        val today = at.toLocalDate()
        val day = picked ?: today
        val items = scheduleItems(day, events, alarms, todos)
        val nowMin = when {
            day == today -> at.hour * 60 + at.minute
            day < today -> 24 * 60
            else -> -1
        }
        PlanState(
            buildTimeline(items, nowMin, showNow = day == today), groupTodos(categories, todos, now),
            todos.filter { it.deletedAt == null }, events, now, day, day == today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanState())

    private val _undo = MutableStateFlow<UndoNotice?>(null)

    /** The change that can currently be undone, if any. */
    val undo: StateFlow<UndoNotice?> = _undo

    private var undoSeq = 0L

    private fun offerUndo(message: String, action: String = "Undo", restore: suspend () -> Unit) {
        _undo.value = UndoNotice(++undoSeq, message, action) { viewModelScope.launch { restore() } }
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
        if (done) offerUndo("Finished “${todo.title.ellipsize()}”") { c.todos.save(todo) }
    }

    /** Soft-deletes [todo] with Undo. */
    fun delete(todo: TodoEntity) = viewModelScope.launch {
        c.todos.delete(todo.id)
        offerUndo("Deleted “${todo.title.ellipsize()}”") { c.todos.save(todo.copy(deletedAt = null)) }
    }

    fun save(todo: TodoEntity) = viewModelScope.launch { c.todos.save(todo) }

    /** Appends a new open to-do to [categoryId]. */
    fun addTodo(title: String, categoryId: String?, dueAt: Long? = null, remind: Boolean = false) = viewModelScope.launch {
        val clean = title.trim()
        if (clean.isEmpty()) return@launch
        val next = state.value.groups.firstOrNull { it.category.id == categoryId }?.open?.maxOfOrNull { it.sort }?.plus(1) ?: 0
        val todo = TodoEntity(newId(), categoryId, clean, dueAt, remind, sort = next)
        c.todos.save(todo)
        offerUndo("Added “${clean.ellipsize()}”") { c.todos.delete(todo.id) }
    }

    /** Drops [id] at [index] among the open to-dos of [categoryId]. */
    fun move(id: String, categoryId: String?, index: Int) = viewModelScope.launch {
        val all = state.value.groups.flatMap { it.open + it.doneToday + it.doneEarlier }
        c.todos.saveAll(moveTodo(all, id, categoryId, index))
    }

    /** Saves an edited event without a notice. */
    fun saveEvent(event: EventEntity) = viewModelScope.launch { c.plan.saveEvent(event) }

    /** Saves a new event and confirms; for another day the notice offers "View" to go there. */
    fun addEvent(event: EventEntity) = viewModelScope.launch {
        c.plan.saveEvent(event)
        val day = event.startAt.toLocalDate()
        if (day != state.value.day) {
            offerUndo("Added for ${day.dayLabel()}", "View") { showDay(day) }
        } else {
            offerUndo("Added “${event.title.ellipsize()}”") { c.plan.saveEvent(event.copy(deletedAt = c.clock.now())) }
        }
    }

    /** Soft-deletes [event] with Undo. */
    fun deleteEvent(event: EventEntity) = viewModelScope.launch {
        c.plan.saveEvent(event.copy(deletedAt = c.clock.now()))
        offerUndo("Deleted “${event.title.ellipsize()}”") { c.plan.saveEvent(event.copy(deletedAt = null)) }
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

private fun String.ellipsize(max: Int = 28) = if (length > max) take(max - 1).trimEnd() + "…" else this
