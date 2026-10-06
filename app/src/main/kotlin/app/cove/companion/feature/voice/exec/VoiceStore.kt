package app.cove.companion.feature.voice.exec

import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** The reads and writes [IntentExecutor] needs, so it can be tested without a database. */
interface VoiceStore {
    suspend fun todoCategories(): List<TodoCategoryEntity>
    suspend fun expenseCategories(): List<ExpenseCategoryEntity>
    suspend fun habits(): List<HabitEntity>
    suspend fun alarms(): List<AlarmEntity>
    suspend fun nextEvent(from: Long): EventEntity?

    suspend fun addTodo(title: String, categoryId: String?, dueAt: Long?, remind: Boolean): TodoEntity
    suspend fun deleteTodo(id: String)
    suspend fun saveAlarm(alarm: AlarmEntity): AlarmEntity
    suspend fun getAlarm(id: String): AlarmEntity?
    suspend fun deleteAlarm(id: String)
    suspend fun saveExpense(expense: ExpenseEntity)
    suspend fun deleteExpense(id: String)
    suspend fun saveHabit(habit: HabitEntity)
    suspend fun deleteHabit(id: String)
    suspend fun isHabitTicked(habitId: String, day: LocalDate): Boolean
    suspend fun toggleHabit(habitId: String, day: LocalDate)
    suspend fun saveJournal(entry: JournalEntryEntity)
    suspend fun deleteJournal(id: String)

    suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?): VoiceCommandEntity
    suspend fun lastCommand(): VoiceCommandEntity?
    suspend fun commandById(id: String): VoiceCommandEntity?
    suspend fun markUndone(id: String)
}

/** [VoiceStore] over the app's repositories; every write goes through them so sync sees it. */
class RepoVoiceStore(private val c: AppContainer) : VoiceStore {
    override suspend fun todoCategories() = c.todos.categories.first()
    override suspend fun expenseCategories() = c.money.categories.first()
    override suspend fun habits() = c.habits.habits.first()
    override suspend fun alarms() = c.plan.alarms.first()
    override suspend fun nextEvent(from: Long) = c.database.events().observeUpcoming(from, 1).first().firstOrNull()

    override suspend fun addTodo(title: String, categoryId: String?, dueAt: Long?, remind: Boolean): TodoEntity {
        val todo = TodoEntity(newId(), categoryId, title, dueAt, remind = remind, source = "voice")
        c.todos.save(todo)
        return todo
    }

    override suspend fun deleteTodo(id: String) = c.todos.delete(id)
    override suspend fun saveAlarm(alarm: AlarmEntity) = c.plan.saveAlarm(alarm)
    override suspend fun getAlarm(id: String) = c.database.alarms().get(id)
    override suspend fun deleteAlarm(id: String) = c.plan.deleteAlarm(id)
    override suspend fun saveExpense(expense: ExpenseEntity) = c.money.save(expense)
    override suspend fun deleteExpense(id: String) = c.money.delete(id)
    override suspend fun saveHabit(habit: HabitEntity) = c.habits.save(habit)
    override suspend fun deleteHabit(id: String) = c.habits.delete(id)
    override suspend fun isHabitTicked(habitId: String, day: LocalDate) =
        c.database.habits().log(habitId, day.toEpochDay())?.let { it.deletedAt == null } == true

    override suspend fun toggleHabit(habitId: String, day: LocalDate) = c.habits.toggle(habitId, day)
    override suspend fun saveJournal(entry: JournalEntryEntity) = c.journal.save(entry)

    override suspend fun deleteJournal(id: String) {
        c.database.journal().get(id)?.let { c.journal.save(it.copy(deletedAt = c.clock.now())) }
    }

    override suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?) =
        c.assistant.recordCommand(transcript, intent, undoPayload)

    override suspend fun lastCommand() = c.assistant.lastCommand()
    override suspend fun commandById(id: String) = c.assistant.commandById(id)
    override suspend fun markUndone(id: String) = c.assistant.markUndone(id)
}
