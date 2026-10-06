package app.cove.companion.feature.voice

import app.cove.companion.core.clockText
import app.cove.companion.core.rupees
import app.cove.companion.core.shortTime
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.ai.model.VoiceIntent
import java.time.LocalDate

private val numberWords = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

/** "Three" for small counts, digits otherwise. */
fun countWord(n: Int) = numberWords.getOrElse(n) { n.toString() }

private fun time(minutes: Int) = clockText(minutes).let { it.digits + it.suffix }

private fun repeatText(mask: Int) = when (mask) {
    0 -> ""
    0b1111111 -> " · every day"
    0b0011111 -> " · weekdays"
    0b1100000 -> " · weekends"
    else -> " · repeats"
}

/** When a reminder fires, as "4 pm", "tomorrow 9 am" or "Friday 9 am"; [today] is the clock's date. */
fun reminderWhen(at: Long, today: LocalDate): String {
    val t = at.toLocalDateTime()
    val day = when (t.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "tomorrow "
        else -> t.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } + " "
    }
    return day + shortTime(t)
}

/** " today" or " tomorrow" for a one-off alarm at [minutes] given the clock's [nowMinutes]; empty for repeating alarms or when unknown. */
fun alarmDay(minutes: Int, daysMask: Int, nowMinutes: Int?): String = when {
    nowMinutes == null || daysMask != 0 -> ""
    minutes > nowMinutes -> " today"
    else -> " tomorrow"
}

/**
 * One-line description of a draft, as listed in the result and partial cards. [nowMinutes] (minutes since midnight)
 * lets a one-off alarm say whether it rings today or tomorrow.
 */
fun describe(intent: VoiceIntent, today: LocalDate, nowMinutes: Int? = null): String = when (intent) {
    is VoiceIntent.SetAlarm ->
        "Alarm for ${time(intent.minutes)}" + alarmDay(intent.minutes, intent.daysMask, nowMinutes) + (intent.label.takeIf { it != "Alarm" }?.let { " · $it" } ?: "") + repeatText(intent.daysMask)
    is VoiceIntent.ChangeAlarm -> "Move alarm to ${time(intent.minutes)}"
    is VoiceIntent.AddReminder -> {
        val whenText = intent.at?.let { reminderWhen(it, today) }
        if (intent.title == "Reminder") "Remind me" + (whenText?.let { " at $it" } ?: "")
        else "Remind me to ${intent.title.replaceFirstChar { it.lowercase() }}" + (whenText?.let { " · $it" } ?: "")
    }
    is VoiceIntent.LogExpense ->
        (if (intent.received) "Received " else "Spent ") + rupees(intent.amountPaise) + (intent.note.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
    is VoiceIntent.LogHabit -> "Tick ${intent.name}"
    is VoiceIntent.AddHabit -> "Add a habit called ${intent.name}"
    is VoiceIntent.JournalNote -> "Journal: " + intent.text.take(40) + if (intent.text.length > 40) "…" else ""
    is VoiceIntent.AddTodos -> intent.items.joinToString { it.title }
    VoiceIntent.QueryNext -> "What's next"
    VoiceIntent.UndoLast -> "Undo the last thing"
}

/** Headline pair (primary, muted tail) for the result screen. */
fun resultHeadline(intents: List<VoiceIntent>, today: LocalDate, nowMinutes: Int? = null): Pair<String, String> {
    val todos = intents.filterIsInstance<VoiceIntent.AddTodos>().sumOf { it.items.size }
    val single = intents.singleOrNull()
    return when {
        todos > 0 && intents.size == 1 -> (if (todos == 1) "One to-do." else "${countWord(todos)} to-dos.") to " Sorted for you."
        single is VoiceIntent.SetAlarm -> "Alarm for ${time(single.minutes)}." to
            (alarmDay(single.minutes, single.daysMask, nowMinutes).trim().replaceFirstChar { it.uppercase() }.let { if (it.isEmpty()) " Check the time." else " $it. Check the time." })
        single is VoiceIntent.ChangeAlarm -> "Move it to ${time(single.minutes)}." to " Check the time."
        single is VoiceIntent.AddReminder -> "A reminder." to (single.at?.let { " ${reminderWhen(it, today).replaceFirstChar { c -> c.uppercase() }}." } ?: " No time set.")
        single is VoiceIntent.AddHabit -> "A new habit." to " Called ${single.name}."
        single is VoiceIntent.LogHabit -> "Tick ${single.name}." to " Just one tap."
        single is VoiceIntent.JournalNote -> "A journal note." to " Kept on this phone."
        else -> "${countWord(intents.size)} things." to " Check them over."
    }
}
