package app.cove.companion.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A clock time split into the digits and the "am"/"pm" suffix, which the design greys out. */
data class ClockText(val digits: String, val suffix: String)

/** 12-hour clock text for minutes since midnight, e.g. 390 -> ("6:30", " am"). */
fun clockText(minutes: Int): ClockText {
    val h24 = (minutes / 60) % 24
    val m = minutes % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    return ClockText("$h12:${m.toString().padStart(2, '0')}", if (h24 < 12) " am" else " pm")
}

fun LocalDateTime.clock(): ClockText = clockText(hour * 60 + minute)

/** Short time like "1 pm" or "10:30 am". */
fun shortTime(dateTime: LocalDateTime): String {
    val c = dateTime.clock()
    val digits = if (dateTime.minute == 0) c.digits.substringBefore(':') else c.digits
    return digits + c.suffix
}

/** Rupee amount from paise with Indian digit grouping; decimals only when non-zero. */
fun rupees(paise: Long, decimals: Boolean = false): String {
    val whole = paise / 100
    val s = whole.toString()
    val grouped = if (s.length <= 3) s else {
        val head = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,${s.takeLast(3)}"
    }
    val frac = paise % 100
    return "₹$grouped" + if (decimals || frac != 0L) ".${frac.toString().padStart(2, '0')}" else ""
}

/** Amount for screen readers: "1,200 rupees" or "840 rupees 50 paise" (a bare "₹" is read inconsistently). */
fun rupeesSpoken(paise: Long): String {
    val whole = rupees(paise - paise % 100).removePrefix("₹")
    val frac = paise % 100
    return "$whole rupees" + if (frac != 0L) " $frac paise" else ""
}

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/** "Tuesday, 6 October". */
fun LocalDate.longLabel(): String = format(longDate)

/** Part of the day used for greetings and layout. */
enum class DayPhase { Morning, Afternoon, Evening }

fun dayPhase(hour: Int) = when {
    hour < 12 -> DayPhase.Morning
    hour < 17 -> DayPhase.Afternoon
    else -> DayPhase.Evening
}

/** "in 25 min", "in 2 h", or "now". */
fun inText(deltaMinutes: Long): String = when {
    deltaMinutes <= 0 -> "now"
    deltaMinutes < 60 -> "in $deltaMinutes min"
    deltaMinutes < 180 -> "in ${deltaMinutes / 60} h ${deltaMinutes % 60} min".replace(" 0 min", "")
    else -> "in ${deltaMinutes / 60} h"
}
