package app.cove.companion.data.repo

import app.cove.companion.core.Clock
import app.cove.companion.core.epochDay
import app.cove.companion.core.newId
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.categorize.CategoryLearning
import app.cove.companion.data.categorize.CategoryTokens
import app.cove.companion.data.categorize.PastProposal
import app.cove.companion.data.categorize.PayeeLearning
import app.cove.companion.data.categorize.PayeeLogic
import app.cove.companion.data.categorize.RetroChange
import app.cove.companion.data.sms.PayeeKey
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.HabitLogEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
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
class SettingsRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog? = null) {
    val settings: Flow<SettingsEntity> = db.settings().observe().map { it ?: SettingsEntity() }

    suspend fun update(change: (SettingsEntity) -> SettingsEntity) {
        val current = db.settings().get() ?: SettingsEntity()
        db.settings().upsert(change(current).copy(updatedAt = clock.now()))
        log?.mark("settings", SettingsEntity.ID)
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

    suspend fun alarm(id: String): AlarmEntity? = db.alarms().get(id)?.takeIf { it.deletedAt == null }

    suspend fun deleteAlarm(id: String) {
        db.alarms().get(id)?.let { saveAlarm(it.copy(deletedAt = clock.now(), enabled = false)) }
    }

    fun eventsOn(day: LocalDate): Flow<List<EventEntity>> =
        db.events().observeBetween(day.startOfDayMillis(), day.plusDays(1).startOfDayMillis() - 1)

    /** Events that repeat and have started by the end of [day]; filter with their repeat rule. */
    fun repeatingEvents(day: LocalDate): Flow<List<EventEntity>> = db.events().observeRepeating(day.plusDays(1).startOfDayMillis() - 1)

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

    suspend fun saveAll(todos: List<TodoEntity>) {
        if (todos.isEmpty()) return
        val now = clock.now()
        db.todos().upsertAll(todos.map { it.copy(updatedAt = now) })
        todos.forEach { log.mark("todos", it.id) }
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

    suspend fun get(id: String) = db.habits().get(id)

    suspend fun delete(id: String) {
        db.habits().get(id)?.let { save(it.copy(deletedAt = clock.now())) }
    }

    /** Toggles the tick for [habitId] on [day] (callers pass the day from the app clock). */
    suspend fun toggle(habitId: String, day: LocalDate = LocalDate.now()) {
        val existing = db.habits().log(habitId, day.toEpochDay())
        val entry = existing?.copy(deletedAt = if (existing.deletedAt == null) clock.now() else null)
            ?: HabitLogEntity(newId(), habitId, day.toEpochDay())
        db.habits().upsertLog(entry.copy(updatedAt = clock.now()))
        log.mark("habit_logs", entry.id)
    }
}

/** One expense moving from category [from] (null = uncategorised) to [to]. */
data class CategoryChange(val expenseId: String, val from: String?, val to: String, val payee: PayeeSnapshot? = null)

/** A payee's memory row before a change (null: there was none), so Undo can put it back exactly. */
data class PayeeSnapshot(val payeeKey: String, val before: PayeeMemoryEntity?)

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

    /** Learned word-to-category memory keyed by token, for [app.cove.companion.data.categorize.ExpenseCategorizer]. */
    val memory: Flow<Map<String, CategoryMemoryEntity>> = db.expenses().observeMemory().map { rows -> rows.associateBy { it.token } }

    /**
     * Remembers that the user filed [note] under [categoryId]. When [previousId] differs, the words are first taken
     * out of that category so a changed mind weakens the old mapping.
     */
    suspend fun teach(note: String, categoryId: String, previousId: String? = null) {
        val tokens = if (PayeeKey.isGenericNote(note)) emptyList() else CategoryTokens.tokens(note).filter { it !in CategoryTokens.genericWords }
        if (tokens.isEmpty()) return
        val now = clock.now()
        val rows = db.expenses().memoryFor(tokens).associateBy { it.token }.toMutableMap()
        for (t in tokens) {
            var row = rows[t]
            if (previousId != null && previousId != categoryId) row = CategoryLearning.unlearn(row, previousId, now) ?: row
            val next = CategoryLearning.learn(row, t, categoryId, now)
            db.expenses().upsertMemory(next)
            log.mark("category_memory", t)
        }
    }

    /** Payee memory keyed by `PayeeKey`, for [app.cove.companion.data.categorize.ExpenseCategorizer] and recall on import. */
    val payeeMemory: Flow<Map<String, PayeeMemoryEntity>> = db.expenses().observePayeeMemory().map { rows -> rows.associateBy { it.payeeKey } }

    /** The live memory of [key], or null. */
    suspend fun payee(key: String): PayeeMemoryEntity? = db.expenses().payeeMemory(key)?.takeIf { it.deletedAt == null && it.count > 0 }

    /** Live memory rows of [keys], by key. */
    suspend fun payees(keys: Collection<String>): Map<String, PayeeMemoryEntity> =
        keys.distinct().chunked(400).flatMap { db.expenses().payeeMemoryFor(it) }.filter { it.deletedAt == null && it.count > 0 }.associateBy { it.payeeKey }

    /**
     * Remembers that the user filed payee [key] under [categoryId] with [label] (null keeps the cleaned [displayName]); see
     * [PayeeLearning.learn]. Explicit user actions only. Returns what was there before, for [restorePayee].
     */
    suspend fun teachPayee(key: String, categoryId: String, label: String?, displayName: String, keepLabel: Boolean = false): PayeeSnapshot {
        val before = db.expenses().payeeMemory(key)
        db.expenses().upsertPayeeMemory(PayeeLearning.learn(before, key, categoryId, label, displayName, clock.now(), keepLabel))
        log.mark("payee_memory", key)
        return PayeeSnapshot(key, before)
    }

    /** Puts a payee's memory back to [snapshot] (Undo), newest first when several touched the same key. */
    suspend fun restorePayee(snapshot: PayeeSnapshot) {
        val now = clock.now()
        val row = snapshot.before?.copy(updatedAt = now) ?: db.expenses().payeeMemory(snapshot.payeeKey)?.let { PayeeLearning.forgotten(it, now) } ?: return
        db.expenses().upsertPayeeMemory(row)
        log.mark("payee_memory", snapshot.payeeKey)
    }

    /** Forgets one payee ("Forget" on the expense); returns the snapshot for Undo. */
    suspend fun forgetPayee(key: String): PayeeSnapshot? {
        val before = db.expenses().payeeMemory(key)?.takeIf { it.deletedAt == null && it.count > 0 } ?: return null
        db.expenses().upsertPayeeMemory(PayeeLearning.forgotten(before, clock.now()))
        log.mark("payee_memory", key)
        return PayeeSnapshot(key, before)
    }

    /** Forgets every payee (Me > forget); expenses and word memory stay. Returns how many were forgotten. */
    suspend fun forgetAllPayees(): Int {
        val now = clock.now()
        val rows = db.expenses().livePayeeMemory()
        rows.forEach {
            db.expenses().upsertPayeeMemory(PayeeLearning.forgotten(it, now))
            log.mark("payee_memory", it.payeeKey)
        }
        return rows.size
    }

    /** Live spent expenses of payee [key]. */
    suspend fun expensesWithPayee(key: String): List<ExpenseEntity> = db.expenses().withPayee(key)

    /** Earlier payments of [keys]' payees that differ from the chosen category/label, per [PayeeLogic.retroChanges]. */
    suspend fun retroChanges(picks: Map<String, Pair<String, String?>>, excludeIds: Set<String>): List<RetroChange> =
        picks.flatMap { (key, pick) -> PayeeLogic.retroChanges(db.expenses().withPayee(key), excludeIds, pick.first, pick.second) }

    /** Applies [changes] to earlier payments without teaching anything; [undoRetro] reverses it. */
    suspend fun applyRetro(changes: List<RetroChange>) {
        for (ch in changes) expense(ch.expenseId)?.let { save(it.copy(categoryId = ch.toCategoryId, note = ch.toNote)) }
    }

    /** Puts the expenses of [changes] back as they were. */
    suspend fun undoRetro(changes: List<RetroChange>) {
        for (ch in changes) expense(ch.expenseId)?.let { save(it.copy(categoryId = ch.fromCategoryId, note = ch.fromNote)) }
    }

    /** Payees the user has filed consistently but Cove has no memory of yet ("Learn from my past payments"). */
    suspend fun pastPayeeProposals(categories: List<ExpenseCategoryEntity>): List<PastProposal> =
        PayeeLogic.pastProposals(db.expenses().spentWithPayee(), db.expenses().livePayeeMemory().map { it.payeeKey }.toSet(), categories)

    /** Creates payee memory for [proposals] (category only, label stays the merchant name); returns snapshots for Undo. */
    suspend fun rememberPayees(proposals: List<PastProposal>): List<PayeeSnapshot> =
        proposals.map { teachPayee(it.payeeKey, it.categoryId, null, it.displayName) }

    /** Takes the words of [note] out of [categoryId] (used when an accepted suggestion is undone). */
    suspend fun forget(note: String, categoryId: String) {
        val tokens = CategoryTokens.tokens(note)
        if (tokens.isEmpty()) return
        val now = clock.now()
        for (row in db.expenses().memoryFor(tokens)) {
            CategoryLearning.unlearn(row, categoryId, now)?.let {
                db.expenses().upsertMemory(it)
                log.mark("category_memory", it.token)
            }
        }
    }

    /** Spent expenses since [from], newest first, for the review list. */
    suspend fun spentSince(from: Long): List<ExpenseEntity> = db.expenses().spentSince(from)

    /** Files each expense under its new category and teaches the memory; returns the changes actually applied. */
    suspend fun applyCategories(changes: List<CategoryChange>): List<CategoryChange> =
        changes.mapNotNull { ch ->
            val e = expense(ch.expenseId) ?: return@mapNotNull null
            save(e.copy(categoryId = ch.to))
            teach(e.note, ch.to, ch.from)
            val snapshot = e.payeeKey?.let { key -> teachPayee(key, ch.to, null, payee(key)?.displayName ?: e.note, keepLabel = true) }
            ch.copy(from = e.categoryId, payee = snapshot)
        }

    /** Reverses [applyCategories]: puts each expense back and forgets what was taught. */
    suspend fun undoCategories(changes: List<CategoryChange>) {
        for (ch in changes) {
            val e = expense(ch.expenseId) ?: continue
            save(e.copy(categoryId = ch.from))
            forget(e.note, ch.to)
        }
        changes.mapNotNull { it.payee }.asReversed().forEach { restorePayee(it) }
    }

    /** Number of live expenses filed under [id], for the delete warning. */
    suspend fun expenseCount(id: String): Int = db.expenses().inCategory(id).size

    /**
     * Removes a category; its expenses fall back to "Other" (no category).
     *
     * @return the ids of the expenses that were moved, to hand to [restoreCategory].
     */
    suspend fun deleteCategory(id: String): List<String> {
        val moved = db.expenses().inCategory(id)
        moved.forEach { save(it.copy(categoryId = null)) }
        db.expenses().getCategory(id)?.let { saveCategory(it.copy(deletedAt = clock.now())) }
        return moved.map { it.id }
    }

    /** Brings back a category removed with [deleteCategory] and files [movedExpenses] under it again. */
    suspend fun restoreCategory(id: String, movedExpenses: List<String>) {
        db.expenses().getCategory(id)?.let { saveCategory(it.copy(deletedAt = null)) }
        movedExpenses.forEach { eid -> db.expenses().get(eid)?.let { save(it.copy(categoryId = id)) } }
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

    suspend fun get(id: String) = db.journal().get(id)

    /** Soft-deletes the entry. */
    suspend fun delete(id: String) {
        db.journal().get(id)?.let { save(it.copy(deletedAt = clock.now())) }
    }

    suspend fun newEntry(day: LocalDate = LocalDate.now()) = JournalEntryEntity(
        id = newId(), day = day.toEpochDay(), createdAt = clock.now(),
    )
}

/** Suggestions, voice command history and cached briefs. */
class AssistantRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog? = null) {
    val activeDecision: Flow<DecisionEntity?> = db.assistant().observeActiveDecision()

    fun brief(day: LocalDate): Flow<BriefEntity?> = db.assistant().observeBrief(day.toEpochDay())

    suspend fun saveBrief(brief: BriefEntity) {
        db.assistant().upsertBrief(brief)
        log?.mark("briefs", brief.day.toString())
    }

    suspend fun saveDecision(decision: DecisionEntity) {
        db.assistant().upsert(decision.copy(updatedAt = clock.now()))
        log?.mark("decisions", decision.id)
    }

    suspend fun shownDecisions() = db.assistant().shown()

    suspend fun decisionsSince(kind: String, since: Long) = db.assistant().countSince(kind, since)

    suspend fun recentConfirmed(kind: String, limit: Int = 2) = db.assistant().recentConfirmed(kind, limit)

    suspend fun isMuted(kind: String) = db.assistant().isMuted(kind) == true

    suspend fun mute(kind: String) {
        db.assistant().setPref(SuggestionPrefEntity(kind, true, clock.now()))
        log?.mark("suggestion_prefs", kind)
    }

    suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?): VoiceCommandEntity {
        val cmd = VoiceCommandEntity(newId(), transcript, intent, undoPayload, createdAt = clock.now())
        db.assistant().insertCommand(cmd)
        log?.mark("voice_commands", cmd.id)
        return cmd
    }

    suspend fun lastCommand() = db.assistant().lastCommand()

    suspend fun commandById(id: String) = db.assistant().command(id)

    suspend fun markUndone(id: String) {
        db.assistant().markUndone(id)
        log?.mark("voice_commands", id)
    }
}

/** Rows waiting to be pushed, so screens can say "will sync" while offline. */
fun CoveDatabase.pendingIds(table: String): Flow<List<String>> = sync().observePendingIds(table)

/** Today's day number in the habit/journal convention. */
fun Clock.today(): Long = now().epochDay()
