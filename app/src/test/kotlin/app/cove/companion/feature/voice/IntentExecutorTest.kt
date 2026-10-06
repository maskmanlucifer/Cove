package app.cove.companion.feature.voice

import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity
import app.cove.companion.feature.voice.exec.IntentExecutor
import app.cove.companion.feature.voice.exec.VoiceStore
import app.cove.companion.feature.voice.intent.TodoDraft
import app.cove.companion.feature.voice.intent.VoiceIntent
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : VoiceStore {
    val todos = mutableMapOf<String, TodoEntity>()
    val alarmMap = mutableMapOf<String, AlarmEntity>()
    val expenses = mutableMapOf<String, ExpenseEntity>()
    val ticks = mutableSetOf<Pair<String, LocalDate>>()
    val journal = mutableMapOf<String, JournalEntryEntity>()
    val commands = mutableListOf<VoiceCommandEntity>()
    private var n = 0

    override suspend fun todoCategories() = listOf(TodoCategoryEntity("c-shop", "Shopping"), TodoCategoryEntity("c-home", "Home"))
    override suspend fun expenseCategories() = listOf(ExpenseCategoryEntity("e-food", "Food"), ExpenseCategoryEntity("e-other", "Other"))
    override suspend fun habits() = listOf(HabitEntity("h-walk", "Walk"))
    override suspend fun alarms() = alarmMap.values.toList()
    override suspend fun nextEvent(from: Long): EventEntity? = null
    override suspend fun addTodo(title: String, categoryId: String?, dueAt: Long?, remind: Boolean) =
        TodoEntity("t${n++}", categoryId, title, dueAt, remind = remind, source = "voice").also { todos[it.id] = it }
    override suspend fun deleteTodo(id: String) { todos.remove(id) }
    override suspend fun saveAlarm(alarm: AlarmEntity) = alarm.also { alarmMap[it.id] = it }
    override suspend fun getAlarm(id: String) = alarmMap[id]
    override suspend fun deleteAlarm(id: String) { alarmMap.remove(id) }
    override suspend fun saveExpense(expense: ExpenseEntity) { expenses[expense.id] = expense }
    override suspend fun deleteExpense(id: String) { expenses.remove(id) }
    override suspend fun isHabitTicked(habitId: String, day: LocalDate) = (habitId to day) in ticks
    override suspend fun toggleHabit(habitId: String, day: LocalDate) {
        if (!ticks.remove(habitId to day)) ticks += habitId to day
    }
    override suspend fun saveJournal(entry: JournalEntryEntity) { journal[entry.id] = entry }
    override suspend fun deleteJournal(id: String) { journal.remove(id) }
    override suspend fun recordCommand(transcript: String, intent: String, undoPayload: String?) =
        VoiceCommandEntity("cmd${n++}", transcript, intent, undoPayload, createdAt = commands.size.toLong()).also { commands += it }
    override suspend fun lastCommand() = commands.lastOrNull()
    override suspend fun commandById(id: String) = commands.firstOrNull { it.id == id }
    override suspend fun markUndone(id: String) {
        val i = commands.indexOfFirst { it.id == id }
        commands[i] = commands[i].copy(undone = true)
    }
}

class IntentExecutorTest {
    private val now = LocalDateTime.of(2026, 10, 6, 10, 35)
    private val store = FakeStore()
    private val exec = IntentExecutor(store, Clock { now.toEpochMillis() })

    private fun run(vararg i: VoiceIntent) = runBlocking { exec.execute("t", i.toList()) }

    @Test
    fun todosMapCategoriesAndUndo() {
        val r = run(VoiceIntent.AddTodos(listOf(TodoDraft("Milk", "shopping"), TodoDraft("Fix tap", "Home"), TodoDraft("X", null))))
        assertEquals("Saved 3 to-dos", r.summary)
        assertEquals(listOf("c-shop", "c-home", null), store.todos.values.map { it.categoryId })
        assertTrue(store.todos.values.all { it.source == "voice" })
        runBlocking { exec.undo(r.commandId) }
        assertTrue(store.todos.isEmpty())
        assertTrue(store.commands.single().undone)
    }

    @Test
    fun alarmReminderExpenseJournalHabit() {
        run(VoiceIntent.SetAlarm(390, "Alarm", 0b11111))
        assertEquals(390, store.alarmMap.values.single().minutes)
        val rem = run(VoiceIntent.AddReminder("Call mum", 123L))
        assertTrue(store.todos.values.single().remind)
        assertTrue(rem.summary.startsWith("Reminder set"))
        run(VoiceIntent.LogExpense(34000, "Food", "Cash", "Lunch"))
        val e = store.expenses.values.single()
        assertEquals(listOf(34000L, "e-food", "Cash", "spent"), listOf(e.amountPaise, e.categoryId, e.paidWith, e.kind))
        run(VoiceIntent.LogExpense(5000, "Nonsense", null, ""))
        assertEquals("e-other", store.expenses.values.last().categoryId)
        run(VoiceIntent.JournalNote("hello"))
        assertEquals("hello", store.journal.values.single().body)
        run(VoiceIntent.LogHabit("walk"))
        assertEquals(1, store.ticks.size)
        assertEquals("Walk is already ticked", run(VoiceIntent.LogHabit("Walk")).summary)
    }

    @Test
    fun changeAlarmAndUndoLastRestores() {
        store.alarmMap["a"] = AlarmEntity("a", "Wake up", 390, kind = "wake")
        run(VoiceIntent.ChangeAlarm(450))
        assertEquals(450, store.alarmMap.getValue("a").minutes)
        val r = run(VoiceIntent.UndoLast)
        assertEquals("Undone", r.summary)
        assertEquals(390, store.alarmMap.getValue("a").minutes)
        assertEquals("Nothing to undo", run(VoiceIntent.UndoLast).summary)
    }

    @Test
    fun undoReversesHabitAndExpense() {
        run(VoiceIntent.LogHabit("Walk"), VoiceIntent.LogExpense(100, null, null, "x"))
        runBlocking { exec.undo(null) }
        assertTrue(store.ticks.isEmpty() && store.expenses.isEmpty())
    }

    @Test
    fun failuresAreReportedNotRecorded() {
        val r = run(VoiceIntent.LogHabit("Yoga"))
        assertEquals(null, r.commandId)
        assertEquals(1, r.failed.size)
        assertTrue(run(VoiceIntent.QueryNext).summary.startsWith("Nothing coming up"))
    }
}
