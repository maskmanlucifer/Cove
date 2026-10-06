package app.cove.companion.data.repo

import app.cove.companion.core.Clock
import app.cove.companion.core.epochDay
import app.cove.companion.core.newId
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.HabitLogEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.data.local.entity.SuggestionPrefEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Records a change for the sync worker. Every repository write goes through [mark]. */
class ChangeLog(private val db: CoveDatabase, private val clock: Clock) {
    suspend fun mark(table: String, id: String, op: String = "upsert") =
        db.sync().enqueue(OutboxEntity(tableName = table, rowId = id, op = op, queuedAt = clock.now()))
}

/** App settings with defaults when nothing is stored yet. */
class SettingsRepository(private val db: CoveDatabase, private val clock: Clock) {
    val settings: Flow<SettingsEntity> = db.settings().observe().map { it ?: SettingsEntity() }

    suspend fun update(change: (SettingsEntity) -> SettingsEntity) {
        val current = db.settings().get() ?: SettingsEntity()
        db.settings().upsert(change(current).copy(updatedAt = clock.now()))
    }
}

/** Alarms and calendar events. */
class PlanRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    val alarms: Flow<List<AlarmEntity>> = db.alarms().observeAll()

    suspend fun saveAlarm(alarm: AlarmEntity): AlarmEntity {
        val saved = alarm.copy(updatedAt = clock.now())
        db.alarms().upsert(saved)
        log.mark("alarms", saved.id)
        return saved
    }

    suspend fun deleteAlarm(id: String) {
        db.alarms().get(id)?.let { saveAlarm(it.copy(deletedAt = clock.now(), enabled = false)) }
    }

    fun eventsOn(day: LocalDate): Flow<List<EventEntity>> =
        db.events().observeBetween(day.startOfDayMillis(), day.plusDays(1).startOfDayMillis() - 1)

    fun upcomingEvents(limit: Int = 5): Flow<List<EventEntity>> = db.events().observeUpcoming(clock.now(), limit)

    suspend fun saveEvent(event: EventEntity) {
        val saved = event.copy(updatedAt = clock.now())
        db.events().upsert(saved)
        log.mark("events", saved.id)
    }
}

/** To-dos and their categories. */
class TodoRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    val categories: Flow<List<TodoCategoryEntity>> = db.todos().observeCategories()
    val todos: Flow<List<TodoEntity>> = db.todos().observeTodos()

    suspend fun add(title: String, categoryId: String?, dueAt: Long? = null, source: String = "manual"): TodoEntity {
        val todo = TodoEntity(newId(), categoryId, title, dueAt, source = source, updatedAt = clock.now())
        db.todos().upsert(todo)
        log.mark("todos", todo.id)
        return todo
    }

    suspend fun save(todo: TodoEntity) {
        db.todos().upsert(todo.copy(updatedAt = clock.now()))
        log.mark("todos", todo.id)
    }

    suspend fun setDone(id: String, done: Boolean) {
        db.todos().get(id)?.let { save(it.copy(done = done, doneAt = if (done) clock.now() else null)) }
    }

    suspend fun delete(id: String) {
        db.todos().get(id)?.let { save(it.copy(deletedAt = clock.now())) }
    }

    suspend fun saveCategory(category: TodoCategoryEntity) {
        db.todos().upsertCategory(category.copy(updatedAt = clock.now()))
        log.mark("todo_categories", category.id)
    }

    /** Moves every open to-do in [from] to [to] (used when a category is deleted). */
    suspend fun moveAll(from: String, to: String?, current: List<TodoEntity>) {
        current.filter { it.categoryId == from }.forEach { save(it.copy(categoryId = to)) }
    }
}

/** Habits and their daily logs. */
class HabitRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    val habits: Flow<List<HabitEntity>> = db.habits().observeHabits()

    fun logs(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>> =
        db.habits().observeLogs(from.toEpochDay(), to.toEpochDay())

    suspend fun save(habit: HabitEntity) {
        db.habits().upsert(habit.copy(updatedAt = clock.now()))
        log.mark("habits", habit.id)
    }

    /** Toggles today's tick for [habitId]. */
    suspend fun toggle(habitId: String, day: LocalDate = LocalDate.now()) {
        val existing = db.habits().log(habitId, day.toEpochDay())
        val entry = existing?.copy(deletedAt = if (existing.deletedAt == null) clock.now() else null)
            ?: HabitLogEntity(newId(), habitId, day.toEpochDay())
        db.habits().upsertLog(entry.copy(updatedAt = clock.now()))
        log.mark("habit_logs", entry.id)
    }
}

/** Spending, income and budgets. */
class MoneyRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    val categories: Flow<List<ExpenseCategoryEntity>> = db.expenses().observeCategories()

    fun expenses(from: Long, to: Long): Flow<List<ExpenseEntity>> = db.expenses().observeBetween(from, to)

    suspend fun save(expense: ExpenseEntity) {
        db.expenses().upsert(expense.copy(updatedAt = clock.now()))
        log.mark("expenses", expense.id)
    }

    suspend fun delete(id: String) {
        db.expenses().get(id)?.let { save(it.copy(deletedAt = clock.now())) }
    }

    suspend fun saveCategory(category: ExpenseCategoryEntity) {
        db.expenses().upsertCategory(category.copy(updatedAt = clock.now()))
        log.mark("expense_categories", category.id)
    }

    suspend fun expense(id: String): ExpenseEntity? = db.expenses().get(id)?.takeIf { it.deletedAt == null }

    /** Brings back an expense removed with [delete] (used by Undo). */
    suspend fun restore(id: String) {
        db.expenses().get(id)?.let { save(it.copy(deletedAt = null)) }
    }

    suspend fun category(id: String): ExpenseCategoryEntity? = db.expenses().getCategory(id)?.takeIf { it.deletedAt == null }

    /** Stores the order of [ids] as the categories' `sort`. */
    suspend fun reorderCategories(ids: List<String>) {
        ids.forEachIndexed { i, id -> db.expenses().getCategory(id)?.takeIf { it.sort != i }?.let { saveCategory(it.copy(sort = i)) } }
    }

    /** Removes a category; its expenses fall back to "Other" (no category). */
    suspend fun deleteCategory(id: String) {
        db.expenses().inCategory(id).forEach { save(it.copy(categoryId = null)) }
        db.expenses().getCategory(id)?.let { saveCategory(it.copy(deletedAt = clock.now())) }
    }
}

/** Journal entries and attached media. */
class JournalRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    val entries: Flow<List<JournalEntryEntity>> = db.journal().observeEntries()

    fun media(entryId: String): Flow<List<JournalMediaEntity>> = db.journal().observeMedia(entryId)

    suspend fun save(entry: JournalEntryEntity) {
        db.journal().upsert(entry.copy(updatedAt = clock.now()))
        log.mark("journal_entries", entry.id)
    }

    suspend fun saveMedia(media: JournalMediaEntity) {
        db.journal().upsertMedia(media.copy(updatedAt = clock.now()))
        log.mark("journal_media", media.id)
    }

    suspend fun newEntry(day: LocalDate = LocalDate.now()) = JournalEntryEntity(
        id = newId(), day = day.toEpochDay(), createdAt = clock.now(),
    )
}

/** Suggestions, voice command history and cached briefs. */
class AssistantRepository(private val db: CoveDatabase, private val clock: Clock) {
    val activeDecision: Flow<DecisionEntity?> = db.assistant().observeActiveDecision()

    fun brief(day: LocalDate): Flow<BriefEntity?> = db.assistant().observeBrief(day.toEpochDay())

    suspend fun saveBrief(brief: BriefEntity) = db.assistant().upsertBrief(brief)

    suspend fun saveDecision(decision: DecisionEntity) = db.assistant().upsert(decision.copy(updatedAt = clock.now()))

    suspend fun isMuted(kind: String) = db.assistant().isMuted(kind) == true

    suspend fun mute(kind: String) = db.assistant().setPref(SuggestionPrefEntity(kind, true, clock.now()))

    suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?): VoiceCommandEntity {
        val cmd = VoiceCommandEntity(newId(), transcript, intent, undoPayload, createdAt = clock.now())
        db.assistant().insertCommand(cmd)
        return cmd
    }

    suspend fun lastCommand() = db.assistant().lastCommand()

    suspend fun markUndone(id: String) = db.assistant().markUndone(id)
}

/** Today's day number in the habit/journal convention. */
fun Clock.today(): Long = now().epochDay()
