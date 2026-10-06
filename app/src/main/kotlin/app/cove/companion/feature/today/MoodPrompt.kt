package app.cove.companion.feature.today

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** When "How was today?" is offered: only around night, and only until one answer is given for that day. */
object MoodPrompt {
    /** First hour (24h) at which the question appears. */
    const val FROM_HOUR = 20

    /** Hour at which the day rolls over; the question belongs to the day that started before it. */
    const val DAY_ROLLS_HOUR = 4

    /** The day [now] counts as, with the day ending at [DAY_ROLLS_HOUR] rather than midnight. */
    fun dayKey(now: LocalDateTime): LocalDate = now.minusHours(DAY_ROLLS_HOUR.toLong()).toLocalDate()

    /** True between 8 pm and 4 am. */
    fun inWindow(now: LocalDateTime): Boolean = now.hour >= FROM_HOUR || now.hour < DAY_ROLLS_HOUR

    /** Show the chips when it is night and [answeredDay] is not today's day key. */
    fun visible(now: LocalDateTime, answeredDay: LocalDate?): Boolean = inWindow(now) && answeredDay != dayKey(now)
}

/** Remembers which day's mood was already chosen, so the chips stay hidden after a restart. Per device, never synced. */
class MoodMemory(context: Context) {
    private val prefs = context.getSharedPreferences("today_mood", Context.MODE_PRIVATE)
    private val _answered = MutableStateFlow(prefs.getString(KEY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    val answered: StateFlow<LocalDate?> = _answered

    /** Records that [day] has its mood. */
    fun answer(day: LocalDate) {
        prefs.edit().putString(KEY, day.toString()).apply()
        _answered.value = day
    }

    private companion object {
        const val KEY = "answered_day"
    }
}
