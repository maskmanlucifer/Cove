package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.SpokenSet

/** Sets read out of a transcript: the lift ([exercise], null when none was named), the sets and a unit when one was said. */
data class ParsedSets(val exercise: String?, val sets: List<SpokenSet>, val unit: String?)

/**
 * Turns "bench, sixty-two and a half for eight, eight and six" into sets (see `docs/TRAINING.md` for the phrases).
 *
 * Supported shapes: `W for R, R and R`; `W for R, W2 for R2`; `W x R`; `N sets of R at W`; `N x R at W`;
 * `W for R for N sets`; a bare `W R R R` when the first number is 20 or more or has a decimal; reps only (`12, 10, 8`).
 * Numbers may be words ("thirty-seven five" is 37.5, "sixty eight point four" is 68.4, "sixty and a half" is 60.5).
 */
object SpokenSets {
    private val opts = setOf(RegexOption.IGNORE_CASE)
    private val number = Regex("(?<![\\d.])\\d+(?:\\.\\d+)?")
    private val forWord = Regex("^\\s*(?:for|x|×|by|times|@)\\s*$", opts)

    /** Parses [raw]; null when it names no lift or holds no sets. [known] are the user's lift names. */
    fun parse(raw: String, known: List<String> = emptyList()): ParsedSets? {
        var t = SpokenNumbers.digitize(raw.lowercase().replace('×', 'x'))
        t = decimals(t)
        val unit = when {
            Regex("\\b(?:lbs?|pounds?)\\b").containsMatchIn(t) -> "lb"
            Regex("\\b(?:kgs?|kilos?|kilograms?)\\b").containsMatchIn(t) -> "kg"
            else -> null
        }
        t = t.replace(Regex("\\b(?:kgs?|kilos?|kilograms?|lbs?|pounds?)\\b"), " ")
        val match = ExerciseNames.find(t, known) ?: return null
        t = t.removeRange(match.range).replace(Regex("\\b(?:reps?|repetitions?|please|i did|i just did|just did|did|logged?|add|record|today|my|the|with|using|and a)\\b"), " ")
        t = t.replace(Regex("\\s+"), " ").trim().trim(',', '.', ' ')
        val sets = setsOf(t) ?: return null
        return ParsedSets(match.name, sets, unit)
    }

    /** Folds spoken fractions into the digits: "62 and a half" and "68 point 4" and "37 5 for". */
    internal fun decimals(text: String): String {
        var t = text
        t = Regex("(\\d+)\\s+and\\s+a\\s+half\\b").replace(t) { "${it.groupValues[1]}.5" }
        t = Regex("(\\d+)\\s*point\\s*((?:\\d\\s*)+)").replace(t) { it.groupValues[1] + "." + it.groupValues[2].replace(" ", "") + " " }
        t = Regex("\\b(\\d{2,3})\\s+5\\b(?=\\s*(?:for|x|by|times|@|kg|kgs|lbs?|,|$))").replace(t) { "${it.groupValues[1]}.5" }
        return t
    }

    private fun setsOf(text: String): List<SpokenSet>? {
        // "3 sets of 8 at 60", "3 x 8 at 60", "3 by 8"
        Regex("(?<![\\d.])(\\d{1,2})\\s*(?:sets?\\s*)?(?:of|x|by)\\s*(\\d{1,3})(?![\\d.])(?:\\s*(?:at|with|@|on)\\s*(\\d+(?:\\.\\d+)?))?").find(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            val reps = m.groupValues[2].toInt()
            if (n in 1..MAX_SETS && reps in 1..MAX_REPS) {
                val w = m.groupValues[3].takeIf { it.isNotEmpty() }?.toDouble()
                return List(n) { SpokenSet(w, reps) }
            }
        }
        // "60 for 8 for 3 sets", "60 for 8, 3 sets"
        var work = text
        var repeat = 1
        Regex("(?:for\\s+)?(\\d{1,2})\\s*sets?\\b").find(work)?.let {
            repeat = it.groupValues[1].toInt().coerceIn(1, MAX_SETS)
            work = work.removeRange(it.range)
        }
        val nums = number.findAll(work).toList()
        if (nums.isEmpty()) return null
        val out = ArrayList<SpokenSet>()
        var weight: Double? = null
        for ((i, n) in nums.withIndex()) {
            val value = n.value.toDouble()
            val next = nums.getOrNull(i + 1)
            val connector = if (next != null) work.substring(n.range.last + 1, next.range.first) else ""
            val startsWeight = next != null && forWord.matches(connector)
            val bareWeight = i == 0 && next != null && !startsWeight && (value >= 20 || '.' in n.value) && nums.none { c -> c !== n && c.range.first > n.range.last && forWord.matches(work.substring(n.range.last + 1, c.range.first)) }
            when {
                startsWeight || bareWeight -> {
                    if (value > MAX_WEIGHT) return null
                    weight = value
                }
                else -> {
                    if ('.' in n.value || value < 1 || value > MAX_REPS) return null
                    out += SpokenSet(weight, value.toInt())
                }
            }
        }
        if (out.isEmpty() || out.size > MAX_SETS) return null
        if (repeat > 1 && out.size == 1) return List(repeat) { out[0] }
        return out
    }

    private const val MAX_SETS = 20
    private const val MAX_REPS = 100
    private const val MAX_WEIGHT = 1000.0
}
