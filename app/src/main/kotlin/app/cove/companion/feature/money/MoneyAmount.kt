package app.cove.companion.feature.money

import app.cove.companion.core.rupees

/** Keypad entry for the Add-expense amount; the amount is held as the digits the user typed. */
object AmountInput {
    private const val MaxWholeDigits = 8

    /** Shown when a digit is refused because the amount is already at its largest. */
    const val LIMIT_HINT = "Largest amount is ₹9,99,99,999"

    /** True when [key] would be dropped only because the whole part already has the most digits allowed. */
    fun wouldOverflow(current: String, key: Char): Boolean =
        key.isDigit() && '.' !in current && current != "0" && current.length >= MaxWholeDigits

    /** Appends a digit or '.' to [current], ignoring input that would not make a valid amount. */
    fun push(current: String, key: Char): String {
        val whole = current.substringBefore('.')
        val hasPoint = '.' in current
        return when {
            key == '.' -> if (hasPoint) current else if (current.isEmpty()) "0." else "$current."
            hasPoint -> if (current.substringAfter('.').length >= 2) current else current + key
            whole == "0" -> key.toString()
            whole.length >= MaxWholeDigits -> current
            else -> current + key
        }
    }

    fun back(current: String): String = current.dropLast(1)

    fun toPaise(current: String): Long {
        val whole = current.substringBefore('.').ifEmpty { "0" }.toLong()
        val frac = current.substringAfter('.', "").padEnd(2, '0').take(2).toLong()
        return whole * 100 + frac
    }

    /** "₹2,50,000" as typed so far, keeping a trailing '.' or typed decimals. */
    fun display(current: String): String {
        val whole = current.substringBefore('.').ifEmpty { "0" }.toLong()
        return rupees(whole * 100) + if ('.' in current) "." + current.substringAfter('.') else ""
    }

    /** The typed text for an existing amount, e.g. 25000 -> "250" and 25050 -> "250.50". */
    fun fromPaise(paise: Long): String {
        val frac = paise % 100
        return (paise / 100).toString() + if (frac == 0L) "" else "." + frac.toString().padStart(2, '0')
    }
}
