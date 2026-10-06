package app.cove.companion.ai.provider.rules

import java.time.LocalDate

/** A day named in a workout phrase: the resolved [date] and where they sit in the text. */
data class WorkoutDay(val date: LocalDate, val range: IntRange)

/**
 * Finds "today", "tomorrow", "Thursday", "this Friday", "next Monday" (also "Thu", "Fri", "Tues") for workout plans.
 * A bare weekday or "this <weekday>" is the next such day counting today; "next <weekday>" is the one after today.
 */
object WorkoutDays {
    private val names = mapOf(
        "mon" to 1, "monday" to 1, "tue" to 2, "tues" to 2, "tuesday" to 2, "wed" to 3, "wednesday" to 3,
        "thu" to 4, "thur" to 4, "thurs" to 4, "thursday" to 4, "fri" to 5, "friday" to 5,
        "sat" to 6, "saturday" to 6, "sun" to 7, "sunday" to 7,
    )
    private val word = Regex(
        "\\b(?:(on|this|next|every)\\s+)?(today|tomorrow|day after tomorrow|${names.keys.joinToString("|")})(?:'s)?\\b",
        RegexOption.IGNORE_CASE,
    )

    /** The first day named in [text], or null. [today] comes from the app clock. */
    fun find(text: String, today: LocalDate): WorkoutDay? {
        for (m in word.findAll(text)) {
            val prefix = m.groupValues[1].lowercase()
            if (prefix == "every") continue
            val name = m.groupValues[2].lowercase()
            val date = when (name) {
                "today" -> today
                "tomorrow" -> today.plusDays(1)
                "day after tomorrow" -> today.plusDays(2)
                else -> {
                    var delta = (names.getValue(name) - today.dayOfWeek.value + 7) % 7
                    if (delta == 0 && prefix == "next") delta = 7
                    today.plusDays(delta.toLong())
                }
            }
            return WorkoutDay(date, m.range)
        }
        return null
    }
}
