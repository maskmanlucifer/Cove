package app.cove.companion.feature.voice.exec

import app.cove.companion.core.Clock
import app.cove.companion.core.clockText
import app.cove.companion.core.newId
import app.cove.companion.core.rupees
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.feature.voice.intent.VoiceIntent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.LocalTime

/** Everything needed to reverse one voice command; stored as JSON in `voice_commands.undo_payload`. */
@Serializable
data class UndoPayload(
    val todos: List<String> = emptyList(),
    val alarms: List<String> = emptyList(),
    val alarmRestore: List<AlarmRestore> = emptyList(),
    val expenses: List<String> = emptyList(),
    val habits: List<HabitTick> = emptyList(),
    val journal: List<String> = emptyList(),
) {
    val isEmpty get() = this == UndoPayload()
}

/** Previous state of an alarm that a command moved. */
@Serializable
data class AlarmRestore(val id: String, val minutes: Int, val enabled: Boolean)

/** A habit tick a command added on [day] (epoch day). */
@Serializable
data class HabitTick(val habitId: String, val day: Long)

/** Outcome of running intents. [summary] is the chip text ("Saved 3 to-dos"); [commandId] feeds Undo. */
data class ExecResult(
    val commandId: String?,
    val summary: String,
    val failed: List<String> = emptyList(),
    /** To-dos created by the command (reminders included), so Today can flag them "New". */
    val createdTodos: List<String> = emptyList(),
)

/** Runs confirmed intents against the repositories and records them so they can be undone. */
class IntentExecutor(private val store: VoiceStore, private val clock: Clock) {
    private val json = Json

    /** Executes [intents] for [transcript]. Query and undo intents are answered but not recorded. */
    suspend fun execute(transcript: String, intents: List<VoiceIntent>): ExecResult {
        intents.singleOrNull()?.let { only ->
            when (only) {
                VoiceIntent.UndoLast -> return undoLast()
                VoiceIntent.QueryNext -> return ExecResult(null, nextUp())
                else -> Unit
            }
        }
        var acc = UndoPayload()
        val summaries = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (intent in intents) {
            val step = run(intent)
            if (step.error != null) failed += step.error else summaries += step.summary
            acc = step.undo.let { u ->
                acc.copy(
                    todos = acc.todos + u.todos, alarms = acc.alarms + u.alarms, alarmRestore = acc.alarmRestore + u.alarmRestore,
                    expenses = acc.expenses + u.expenses, habits = acc.habits + u.habits, journal = acc.journal + u.journal,
                )
            }
        }
        val command = if (acc.isEmpty) null else store.recordCommand(
            transcript, intents.joinToString(",") { it.type }, json.encodeToString(acc),
        )
        val summary = when {
            summaries.size == 1 -> summaries.single()
            summaries.isEmpty() -> failed.firstOrNull() ?: "Nothing saved"
            else -> "Saved ${summaries.size} things"
        }
        return ExecResult(command?.id, summary, failed, acc.todos)
    }

    /** Reverses the command [commandId], or the most recent one when null. */
    suspend fun undo(commandId: String?): ExecResult {
        val cmd = (if (commandId == null) store.lastCommand() else store.commandById(commandId))
            ?.takeIf { !it.undone && it.undoPayload != null } ?: return ExecResult(null, "Nothing to undo")
        val p = json.decodeFromString<UndoPayload>(cmd.undoPayload!!)
        p.todos.forEach { store.deleteTodo(it) }
        p.alarms.forEach { store.deleteAlarm(it) }
        p.alarmRestore.forEach { r ->
            store.getAlarm(r.id)?.let { store.saveAlarm(it.copy(minutes = r.minutes, enabled = r.enabled)) }
        }
        p.expenses.forEach { store.deleteExpense(it) }
        p.habits.forEach { t ->
            val day = java.time.LocalDate.ofEpochDay(t.day)
            if (store.isHabitTicked(t.habitId, day)) store.toggleHabit(t.habitId, day)
        }
        p.journal.forEach { store.deleteJournal(it) }
        store.markUndone(cmd.id)
        return ExecResult(null, "Undone")
    }

    private suspend fun undoLast() = undo(null)

    private class Step(val summary: String = "", val undo: UndoPayload = UndoPayload(), val error: String? = null)

    private suspend fun run(intent: VoiceIntent): Step = when (intent) {
        is VoiceIntent.AddTodos -> addTodos(intent)
        is VoiceIntent.AddReminder -> {
            val todo = store.addTodo(intent.title, null, intent.at, remind = true)
            val whenText = intent.at?.let { " for " + dayTime(it) }.orEmpty()
            Step("Reminder set$whenText", UndoPayload(todos = listOf(todo.id)))
        }
        is VoiceIntent.SetAlarm -> {
            val kind = if (intent.label.contains("bed", true)) "bedtime" else "wake"
            val alarm = store.saveAlarm(AlarmEntity(newId(), intent.label, intent.minutes, intent.daysMask, kind))
            Step("Alarm set for ${clockText(intent.minutes).let { it.digits + it.suffix }}", UndoPayload(alarms = listOf(alarm.id)))
        }
        is VoiceIntent.ChangeAlarm -> changeAlarm(intent)
        is VoiceIntent.LogExpense -> logExpense(intent)
        is VoiceIntent.LogHabit -> logHabit(intent)
        is VoiceIntent.JournalNote -> {
            val now = clock.now()
            val entry = JournalEntryEntity(newId(), now.toLocalDate().toEpochDay(), body = intent.text, createdAt = now)
            store.saveJournal(entry)
            Step("Saved to your journal", UndoPayload(journal = listOf(entry.id)))
        }
        VoiceIntent.QueryNext, VoiceIntent.UndoLast -> Step(error = "That can't be combined with other requests")
    }

    private suspend fun addTodos(intent: VoiceIntent.AddTodos): Step {
        val categories = store.todoCategories()
        val ids = intent.items.map { item ->
            val cat = categories.firstOrNull { it.name.equals(item.category, ignoreCase = true) }
            store.addTodo(item.title, cat?.id, item.dueAt, remind = false).id
        }
        return Step("Saved ${ids.size} to-do${if (ids.size == 1) "" else "s"}", UndoPayload(todos = ids))
    }

    private suspend fun changeAlarm(intent: VoiceIntent.ChangeAlarm): Step {
        val alarms = store.alarms()
        val target = intent.label?.let { l -> alarms.firstOrNull { it.kind.equals(l, true) || it.label.contains(l, true) } }
            ?: alarms.firstOrNull { it.kind == "wake" && it.enabled } ?: alarms.firstOrNull()
            ?: return Step(error = "I couldn't find an alarm to change")
        store.saveAlarm(target.copy(minutes = intent.minutes, enabled = true))
        val t = clockText(intent.minutes)
        return Step(
            "Alarm moved to ${t.digits}${t.suffix}",
            UndoPayload(alarmRestore = listOf(AlarmRestore(target.id, target.minutes, target.enabled))),
        )
    }

    private suspend fun logExpense(intent: VoiceIntent.LogExpense): Step {
        val categories = store.expenseCategories()
        val cat = if (intent.received) null else categories.firstOrNull { it.name.equals(intent.category, true) }
            ?: categories.firstOrNull { it.name.equals("Other", true) }
        val expense = ExpenseEntity(
            newId(), intent.amountPaise, if (intent.received) "received" else "spent", cat?.id, intent.note,
            intent.paidWith ?: "UPI", intent.at ?: clock.now(), source = "voice",
        )
        store.saveExpense(expense)
        val label = if (intent.received) "Received" else "Logged"
        return Step("$label ${rupees(intent.amountPaise)}" + (cat?.let { " · ${it.name}" } ?: ""), UndoPayload(expenses = listOf(expense.id)))
    }

    private suspend fun logHabit(intent: VoiceIntent.LogHabit): Step {
        val name = intent.name.lowercase()
        val habit = store.habits().firstOrNull { it.name.equals(intent.name, true) }
            ?: store.habits().firstOrNull { name in it.name.lowercase() || it.name.lowercase() in name }
            ?: return Step(error = "I couldn't find a habit called ${intent.name}")
        val day = clock.now().toLocalDate()
        if (store.isHabitTicked(habit.id, day)) return Step("${habit.name} is already ticked")
        store.toggleHabit(habit.id, day)
        return Step("Ticked ${habit.name}", UndoPayload(habits = listOf(HabitTick(habit.id, day.toEpochDay()))))
    }

    /** Spoken answer for "what's next": the earlier of the next event and the next alarm. */
    private suspend fun nextUp(): String {
        val now = clock.now().toLocalDateTime()
        val event = store.nextEvent(clock.now())
        val alarm = store.alarms().filter { it.enabled }.mapNotNull { a -> nextAlarmAt(a, now)?.let { a to it } }.minByOrNull { it.second }
        val eventAt = event?.startAt?.toLocalDateTime()
        return when {
            event != null && eventAt != null && (alarm == null || eventAt <= alarm.second) ->
                "Next is ${event.title} at ${dayTime(event.startAt)}"
            alarm != null -> "Next is your ${alarm.first.label.lowercase()} alarm at ${clockText(alarm.first.minutes).let { it.digits + it.suffix }}"
            else -> "Nothing coming up"
        }
    }

    private fun nextAlarmAt(a: AlarmEntity, now: LocalDateTime): LocalDateTime? {
        for (offset in 0L..7L) {
            val day = now.toLocalDate().plusDays(offset)
            val on = a.daysMask == 0 || a.daysMask and (1 shl (day.dayOfWeek.value - 1)) != 0
            val at = LocalDateTime.of(day, LocalTime.of(a.minutes / 60, a.minutes % 60))
            if (on && at.isAfter(now)) return at
        }
        return null
    }

    private fun dayTime(millis: Long): String {
        val t = millis.toLocalDateTime()
        val c = clockText(t.hour * 60 + t.minute)
        val today = clock.now().toLocalDate()
        val day = when (t.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "tomorrow "
            else -> t.toLocalDate().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } + " "
        }
        return day + c.digits + c.suffix
    }
}
