package app.cove.companion.feature.training.engine

/** Keypad entry of a body weight: up to three whole digits and one decimal. */
object BodyWeightInput {
    /** Adds [key] (a digit or '.') to [current], ignoring input that would not make a sensible weight. */
    fun push(current: String, key: Char): String {
        val hasPoint = '.' in current
        return when {
            key == '.' -> if (hasPoint) current else if (current.isEmpty()) "0." else "$current."
            hasPoint -> if (current.substringAfter('.').length >= 1) current else current + key
            current == "0" -> key.toString()
            current.length >= 3 -> current
            else -> current + key
        }
    }

    fun back(current: String): String = current.dropLast(1)

    /** The typed weight, or null when it is empty or outside 20 to 400 kg (in the shown [unit]'s terms). */
    fun value(text: String, unit: WeightUnit): Double? {
        val v = text.trimEnd('.').toDoubleOrNull() ?: return null
        val kg = unit.toKg(v)
        return v.takeIf { kg in 20.0..400.0 }
    }
}
