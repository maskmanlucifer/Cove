package app.cove.companion.ai.model

/** One thing the user asked for, parsed from a transcript. Intents are drafts until the executor runs them. */
sealed interface VoiceIntent {
    /** Wire name used in the JSON schema and in `voice_commands.intent`. */
    val type: String

    /** Wake or custom alarm at [minutes] since midnight; [daysMask] as in `AlarmEntity` (0 = once). */
    data class SetAlarm(val minutes: Int, val label: String = "Alarm", val daysMask: Int = 0) : VoiceIntent {
        override val type get() = "set_alarm"
    }

    /** Moves the alarm matching [label] (or the wake alarm) to [minutes]. */
    data class ChangeAlarm(val minutes: Int, val label: String? = null) : VoiceIntent {
        override val type get() = "change_alarm"
    }

    /** One or more to-dos; each carries its own category guess. */
    data class AddTodos(val items: List<TodoDraft>) : VoiceIntent {
        override val type get() = "add_todo"
    }

    /** A to-do with a reminder at [at] (epoch millis), or none when the time was not given. */
    data class AddReminder(val title: String, val at: Long?) : VoiceIntent {
        override val type get() = "add_reminder"
    }

    /** An expense (or income when [received]); [category] is a name guess matched to categories later. */
    data class LogExpense(
        val amountPaise: Long,
        val category: String?,
        val paidWith: String?,
        val note: String,
        val received: Boolean = false,
        val at: Long? = null,
    ) : VoiceIntent {
        override val type get() = "log_expense"
    }

    /** Ticks today's habit called [name]. */
    data class LogHabit(val name: String) : VoiceIntent {
        override val type get() = "log_habit"
    }

    /** A journal note. Never leaves the device. */
    data class JournalNote(val text: String) : VoiceIntent {
        override val type get() = "journal_note"
    }

    data object QueryNext : VoiceIntent {
        override val type get() = "query_next"
    }

    data object UndoLast : VoiceIntent {
        override val type get() = "undo_last"
    }
}

/** A to-do the user has not saved yet; [category] is a category name or null. */
data class TodoDraft(val title: String, val category: String?, val dueAt: Long? = null)
