package app.cove.companion.feature.plan

import app.cove.companion.core.shortTime
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Open to-dos beyond this many sit behind "Show all". */
const val VISIBLE_OPEN_LIMIT = 6

/**
 * A category with its to-dos. [open] are in list order; [doneToday] stay inline (struck) until tomorrow,
 * [doneEarlier] sit behind the "N done" row.
 */
data class CategoryGroup(
    val category: TodoCategoryEntity,
    val open: List<TodoEntity>,
    val doneToday: List<TodoEntity>,
    val doneEarlier: List<TodoEntity>,
) {
    val isEmpty get() = open.isEmpty() && doneToday.isEmpty() && doneEarlier.isEmpty()
}

/** Splits [todos] by category (in [categories] order) and by open/done state as of [now]. */
fun groupTodos(categories: List<TodoCategoryEntity>, todos: List<TodoEntity>, now: Long): List<CategoryGroup> {
    val today = now.toLocalDate()
    val live = todos.filter { it.deletedAt == null }
    return categories.filter { it.deletedAt == null }.sortedBy { it.sort }.map { cat ->
        val mine = live.filter { it.categoryId == cat.id }
        val (done, open) = mine.partition { it.done }
        val (doneToday, doneEarlier) = done.sortedBy { it.sort }.partition { (it.doneAt ?: now).toLocalDate() == today }
        CategoryGroup(cat, open.sortedBy { it.sort }, doneToday, doneEarlier)
    }
}

/** "Today", "Tomorrow", a weekday within the next week, else "12 Oct". */
fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

/** Short due label for a list row: the time when due today, otherwise [relativeDay]. */
fun dueLabel(dueAt: Long?, now: Long): String? {
    if (dueAt == null) return null
    val date = dueAt.toLocalDate()
    val today = now.toLocalDate()
    val d = dueAt.toLocalDateTime()
    return if (date == today && (d.hour != 0 || d.minute != 0)) shortTime(d) else relativeDay(date, today)
}

/**
 * Moves [id] so it is the [toIndex]-th open to-do of [toCategory] and returns every to-do whose
 * category or sort changed. Open items of the touched categories are renumbered 0..n.
 */
fun moveTodo(all: List<TodoEntity>, id: String, toCategory: String?, toIndex: Int): List<TodoEntity> {
    val moving = all.firstOrNull { it.id == id } ?: return emptyList()
    val fromCategory = moving.categoryId
    fun openOf(category: String?) = all.filter { it.categoryId == category && !it.done && it.deletedAt == null }.sortedBy { it.sort }

    val target = openOf(toCategory).filter { it.id != id }.toMutableList()
    target.add(toIndex.coerceIn(0, target.size), moving.copy(categoryId = toCategory))
    val result = mutableMapOf<String, TodoEntity>()
    target.forEachIndexed { i, t -> result[t.id] = t.copy(sort = i) }
    if (fromCategory != toCategory) {
        openOf(fromCategory).filter { it.id != id }.forEachIndexed { i, t -> result[t.id] = t.copy(sort = i) }
    }
    val before = all.associateBy { it.id }
    return result.values.filter { it != before[it.id] }
}

/**
 * Index among the open rows (their vertical centres in [centers], top to bottom) where a row dragged to
 * [y] should land.
 */
fun dropIndex(centers: List<Float>, y: Float): Int = centers.count { it < y }
