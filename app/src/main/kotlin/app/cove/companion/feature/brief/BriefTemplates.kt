package app.cove.companion.feature.brief

import app.cove.companion.core.clockText
import app.cove.companion.core.rupees

/** Weather facts for the day; [description] is a plain word like "clear" or "light rain". */
data class WeatherFacts(val tempC: Int, val highC: Int, val description: String, val rainChance: Int) {
    /** Feel word used in the chip, e.g. "mild". */
    val feel: String
        get() = when {
            tempC < 12 -> "cold"
            tempC < 20 -> "cool"
            tempC < 28 -> "mild"
            tempC < 34 -> "warm"
            else -> "hot"
        }
}

/** A timed item of the day (event or calendar entry), minutes since midnight. */
data class DayItem(val minutes: Int, val title: String, val place: String? = null)

/** Everything the script is built from. Never contains journal content. */
data class BriefFacts(
    val name: String = "",
    val weather: WeatherFacts? = null,
    val items: List<DayItem> = emptyList(),
    val todoTitles: List<String> = emptyList(),
    val moneyLeftPaise: Long? = null,
    val habitsTotal: Int = 0,
    val habitsDone: Int = 0,
    val intro: String? = null,
    val thought: String? = null,
)

/** Builds the brief's segments from [BriefFacts] with fixed templates. */
object BriefTemplates {
    /** Gentle built-in lines used when no generated thought is available. */
    val thoughts = listOf(
        "A slow start is still a start. Today only needs a few things from you.",
        "You do not have to finish everything. Finishing one thing well is plenty.",
        "Drink some water, open a window, and take the first thing first.",
        "Rest is part of the plan, not a break from it.",
    )

    fun build(f: BriefFacts, dayOfYear: Int = 0): List<BriefSegment> = buildList {
        f.weather?.let { add(weather(f, it)) }
        add(day(f))
        f.moneyLeftPaise?.let { add(money(it)) }
        add(BriefSegment("One thing to read", f.thought ?: thoughts[Math.floorMod(dayOfYear, thoughts.size)]))
    }

    private fun weather(f: BriefFacts, w: WeatherFacts): BriefSegment {
        val hello = f.intro ?: if (f.name.isBlank()) "Good morning." else "Good morning, ${f.name}."
        val rain = if (w.rainChance >= 40) " There is a ${w.rainChance} percent chance of rain, so maybe take an umbrella." else ""
        return BriefSegment(
            "Weather · ${w.feel}, ${w.tempC}°",
            "$hello It is ${w.feel} and ${w.description}, ${w.tempC} degrees now, up to ${w.highC} later.$rain",
        )
    }

    private fun day(f: BriefFacts): BriefSegment {
        val parts = mutableListOf<String>()
        if (f.weather == null) parts += f.intro ?: if (f.name.isBlank()) "Good morning." else "Good morning, ${f.name}."
        val items = f.items.sortedBy { it.minutes }
        if (items.isEmpty()) parts += "Nothing is planned, so the day is yours."
        else items.take(3).forEach { parts += "${it.title} at ${spoken(it.minutes)}${it.place?.let { p -> " at $p" }.orEmpty()}." }
        if (items.size > 3) parts += "${items.size - 3} more after that."
        if (f.todoTitles.isNotEmpty()) {
            val n = f.todoTitles.size
            parts += "$n ${if (n == 1) "thing" else "things"} on your list, starting with ${f.todoTitles.first()}."
        }
        if (f.habitsTotal > 0 && f.habitsDone < f.habitsTotal) parts += "${f.habitsTotal - f.habitsDone} of your habits are still open."
        return BriefSegment("Your day", parts.joinToString(" "))
    }

    private fun money(leftPaise: Long): BriefSegment {
        val whole = rupees((kotlin.math.abs(leftPaise) + 50) / 100 * 100)
        return if (leftPaise >= 0) BriefSegment("Money · $whole left", "You have $whole left this month. No rush.")
        else BriefSegment("Money · $whole over", "You are $whole over this month. No rush.")
    }

    /** "eleven" style for whole hours, otherwise "10:30 am". */
    fun spoken(minutes: Int): String {
        val c = clockText(minutes)
        return if (minutes % 60 == 0) "${c.digits.substringBefore(':')}${c.suffix}".trim() else "${c.digits}${c.suffix}"
    }
}
