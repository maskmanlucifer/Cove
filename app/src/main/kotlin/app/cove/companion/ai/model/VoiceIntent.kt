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

    /** Starts a new daily habit called [name]. */
    data class AddHabit(val name: String) : VoiceIntent {
        override val type get() = "add_habit"
    }

    /** Ticks today's habit called [name]. */
    data class LogHabit(val name: String) : VoiceIntent {
        override val type get() = "log_habit"
    }

    /** A journal note. Never leaves the device. */
    data class JournalNote(val text: String) : VoiceIntent {
        override val type get() = "journal_note"
    }

    /**
     * Sets of one lift said aloud ("bench, sixty-two and a half for eight, eight and six"), logged against today's
     * workout. [exercise] is the lift's name as understood; [unit] is `kg` or `lb` only when the user said so.
     */
    data class LogSets(val exercise: String, val sets: List<SpokenSet>, val unit: String? = null) : VoiceIntent {
        override val type get() = "log_sets"
    }

    /**
     * Plans [exercise] on [date]'s weekday ("plan tomorrow bench press 60 kilos 3 sets of 8"). Missing [weight], [sets]
     * and [reps] keep what the lift already has there (3 x 8 for a new one).
     */
    data class PlanExercise(
        val date: java.time.LocalDate,
        val exercise: String,
        val weight: Double? = null,
        val sets: Int? = null,
        val reps: Int? = null,
        val unit: String? = null,
    ) : VoiceIntent {
        override val type get() = "plan_exercise"
    }

    /** Changes today's planned weight of [exercise] ("change my bench to sixty two and a half"). */
    data class ChangeWeight(val exercise: String, val weight: Double, val unit: String? = null) : VoiceIntent {
        override val type get() = "change_weight"
    }

    /** A morning weigh-in of [weight] in [unit] (`kg` or `lb`; null means the user's own unit). */
    data class LogBodyWeight(val weight: Double, val unit: String? = null) : VoiceIntent {
        override val type get() = "log_body_weight"
    }

    /** "What is my workout today?" */
    data object QueryNextWorkout : VoiceIntent {
        override val type get() = "next_workout"
    }

    data object QueryNext : VoiceIntent {
        override val type get() = "query_next"
    }

    data object UndoLast : VoiceIntent {
        override val type get() = "undo_last"
    }
}

/** "Today", "Tomorrow", or the weekday's name ("Thursday") of [date] seen from [today]. */
fun workoutDayLabel(date: java.time.LocalDate, today: java.time.LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
}

/** A to-do the user has not saved yet; [category] is a category name or null. */
data class TodoDraft(val title: String, val category: String?, val dueAt: Long? = null)

/** One set as spoken: [weight] is null when only reps were said (the executor then uses the planned weight). */
data class SpokenSet(val weight: Double?, val reps: Int)
