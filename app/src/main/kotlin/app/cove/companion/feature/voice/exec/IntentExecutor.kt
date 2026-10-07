package app.cove.companion.feature.voice.exec

import app.cove.companion.core.Clock
import app.cove.companion.core.clockText
import app.cove.companion.core.newId
import app.cove.companion.core.rupees
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.feature.training.voice.TrainingOutcome
import app.cove.companion.feature.training.voice.TrainingUndo
import app.cove.companion.feature.training.voice.TrainingVoice
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
    /** Memories a command added, and the older ones they replaced (brought back on undo). */
    val memories: List<String> = emptyList(),
    val memoriesReplaced: List<String> = emptyList(),
    val habitsCreated: List<String> = emptyList(),
    /** Plan, log and weigh-in changes a training command made. */
    val training: TrainingUndo = TrainingUndo(),
    /** What undoing says it did, e.g. "removed 'Buy milk'". */
    val labels: List<String> = emptyList(),
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
class IntentExecutor(private val store: VoiceStore, private val clock: Clock, private val training: TrainingVoice? = null) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Executes [intents] for [transcript]. Query and undo intents are answered but not recorded. */
    suspend fun execute(transcript: String, intents: List<VoiceIntent>): ExecResult {
        intents.singleOrNull()?.let { only ->
            when (only) {
                VoiceIntent.UndoLast -> return undoLast()
                VoiceIntent.QueryNext -> return ExecResult(null, nextUp())
                is VoiceIntent.Recall -> return ExecResult(null, store.recall(only.question))
                VoiceIntent.QueryNextWorkout -> return ExecResult(null, training?.todaySummary() ?: "Training isn't available")
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
                acc.copy(training = acc.training + u.training,
                    todos = acc.todos + u.todos, alarms = acc.alarms + u.alarms, alarmRestore = acc.alarmRestore + u.alarmRestore,
                    expenses = acc.expenses + u.expenses, habits = acc.habits + u.habits, journal = acc.journal + u.journal, memories = acc.memories + u.memories,
                    memoriesReplaced = acc.memoriesReplaced + u.memoriesReplaced,
                    habitsCreated = acc.habitsCreated + u.habitsCreated, labels = acc.labels + u.labels,
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
        p.memories.forEach { store.undoMemory(it, p.memoriesReplaced) }
        p.habitsCreated.forEach { store.deleteHabit(it) }
        if (!p.training.isEmpty) training?.undo(p.training)
        store.markUndone(cmd.id)
        return ExecResult(null, if (p.labels.isEmpty()) "Undone" else "Undone: " + p.labels.joinToString(", "))
    }

    private suspend fun undoLast() = undo(null)

    private class Step(val summary: String = "", val undo: UndoPayload = UndoPayload(), val error: String? = null, val route: String? = null)

    private fun step(o: TrainingOutcome) = Step(
        o.summary, UndoPayload(training = o.undo, labels = if (o.label.isEmpty()) emptyList() else listOf(o.label)), o.error,
    )

    private suspend fun run(intent: VoiceIntent): Step = when (intent) {
        is VoiceIntent.AddTodos -> addTodos(intent)
        is VoiceIntent.AddReminder -> {
            val todo = store.addTodo(intent.title, null, intent.at, remind = true)
            val whenText = intent.at?.let { " for " + dayTime(it) }.orEmpty()
            Step("Reminder set$whenText", UndoPayload(todos = listOf(todo.id), labels = listOf("removed reminder '${intent.title}'")))
        }
        is VoiceIntent.SetAlarm -> {
            val kind = if (intent.label.contains("bed", true)) "bedtime" else "wake"
            val alarm = store.saveAlarm(AlarmEntity(newId(), intent.label, intent.minutes, intent.daysMask, kind))
            val at = clockText(intent.minutes).let { it.digits + it.suffix }
            Step("Alarm set for $at", UndoPayload(alarms = listOf(alarm.id), labels = listOf("removed alarm for $at")))
        }
        is VoiceIntent.ChangeAlarm -> changeAlarm(intent)
        is VoiceIntent.LogExpense -> logExpense(intent)
        is VoiceIntent.LogHabit -> logHabit(intent)
        is VoiceIntent.JournalNote -> {
            val now = clock.now()
            val entry = JournalEntryEntity(newId(), now.toLocalDate().toEpochDay(), body = intent.text, createdAt = now)
            store.saveJournal(entry)
            Step("Saved to your journal", UndoPayload(journal = listOf(entry.id), labels = listOf("removed journal note")))
        }
        is VoiceIntent.Remember -> {
            val saved = store.addMemory(intent.text, intent.subject, intent.detail, intent.kind, intent.keepForMs)
            Step("Remembered", UndoPayload(memories = listOf(saved.id), memoriesReplaced = saved.replaced, labels = listOf("forgot '${intent.subject}'")))
        }
        is VoiceIntent.Recall -> Step(store.recall(intent.question))
        is VoiceIntent.AddHabit -> {
            val habit = HabitEntity(newId(), intent.name, sort = store.habits().size)
            store.saveHabit(habit)
            Step("Added habit ${intent.name}", UndoPayload(habitsCreated = listOf(habit.id), labels = listOf("removed habit '${intent.name}'")))
        }
        is VoiceIntent.LogSets -> training?.let { step(it.logSets(intent)) } ?: Step(error = "Training isn't available")
        is VoiceIntent.PlanExercise -> training?.let { step(it.planExercise(intent)) } ?: Step(error = "Training isn't available")
        is VoiceIntent.ChangeWeight -> training?.let { step(it.changeWeight(intent)) } ?: Step(error = "Training isn't available")
        is VoiceIntent.LogBodyWeight -> training?.let { step(it.logBodyWeight(intent)) } ?: Step(error = "Training isn't available")
        VoiceIntent.QueryNext, VoiceIntent.QueryNextWorkout, VoiceIntent.UndoLast -> Step(error = "That can't be combined with other requests")
    }

    private suspend fun addTodos(intent: VoiceIntent.AddTodos): Step {
        val categories = store.todoCategories()
        val ids = intent.items.map { item ->
            val cat = categories.firstOrNull { it.name.equals(item.category, ignoreCase = true) }
            store.addTodo(item.title, cat?.id, item.dueAt, remind = false).id
        }
        val what = intent.items.joinToString(", ") { "'${it.title}'" }
        return Step("Saved ${ids.size} to-do${if (ids.size == 1) "" else "s"}", UndoPayload(todos = ids, labels = listOf("removed $what")))
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
            UndoPayload(alarmRestore = listOf(AlarmRestore(target.id, target.minutes, target.enabled)), labels = listOf("alarm moved back")),
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
        return Step("$label ${rupees(intent.amountPaise)}" + (cat?.let { " · ${it.name}" } ?: ""), UndoPayload(expenses = listOf(expense.id), labels = listOf("removed ${rupees(intent.amountPaise)} expense")))
    }

    private suspend fun logHabit(intent: VoiceIntent.LogHabit): Step {
        val name = intent.name.lowercase()
        val habit = store.habits().firstOrNull { it.name.equals(intent.name, true) }
            ?: store.habits().firstOrNull { name in it.name.lowercase() || it.name.lowercase() in name }
            ?: return Step(error = "I couldn't find a habit called ${intent.name}")
        val day = clock.now().toLocalDate()
        if (store.isHabitTicked(habit.id, day)) return Step("${habit.name} is already ticked")
        store.toggleHabit(habit.id, day)
        return Step("Ticked ${habit.name}", UndoPayload(habits = listOf(HabitTick(habit.id, day.toEpochDay())), labels = listOf("unticked ${habit.name}")))
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
