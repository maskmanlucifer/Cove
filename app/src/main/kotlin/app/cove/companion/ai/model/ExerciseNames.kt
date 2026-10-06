package app.cove.companion.ai.model

/** A lift found in a transcript: its [name] and where the words sit in the searched text. */
data class ExerciseMatch(val name: String, val range: IntRange)

/**
 * Finds a lift's name in spoken text with aliases and light fuzziness ("bench", "ohp", "rdl", "pull ups").
 * [known] are the user's own lift names; with none known the built-in names are returned so the rules still work.
 */
object ExerciseNames {
    /** Built-in names, also the names of the default programme. */
    val defaults = listOf(
        "Bench press", "Overhead press", "Incline dumbbell", "Triceps pushdown", "Dips", "Barbell row", "Lat pulldown",
        "Pull-ups", "Biceps curl", "Squat", "Romanian deadlift", "Leg press", "Lunges", "Calf raise", "Deadlift",
    )

    /** spoken phrase to the built-in name it means. */
    private val aliases: Map<String, String> = buildMap {
        fun add(name: String, vararg phrases: String) = phrases.forEach { put(it, name) }
        add("Bench press", "bench press", "bench", "flat bench", "barbell bench", "chest press")
        add("Overhead press", "overhead press", "ohp", "press", "shoulder press", "military press", "standing press", "overhead")
        add("Incline dumbbell", "incline dumbbell", "incline dumbbell press", "incline press", "incline db", "incline", "incline bench")
        add("Triceps pushdown", "triceps pushdown", "tricep pushdown", "triceps", "tricep", "pushdown", "pushdowns", "triceps extension")
        add("Dips", "dips", "dip", "chest dips", "tricep dips")
        add("Barbell row", "barbell row", "bent over row", "row", "rows", "bb row", "pendlay row")
        add("Lat pulldown", "lat pulldown", "pulldown", "pull down", "lats", "lat pull down")
        add("Pull-ups", "pull ups", "pull up", "pullups", "pullup", "chin ups", "chin up", "chinups")
        add("Biceps curl", "biceps curl", "bicep curl", "biceps", "bicep", "curl", "curls", "dumbbell curl")
        add("Squat", "squat", "squats", "back squat", "barbell squat")
        add("Romanian deadlift", "romanian deadlift", "romanian", "rdl", "stiff leg deadlift", "stiff legged deadlift")
        add("Leg press", "leg press")
        add("Lunges", "lunges", "lunge", "walking lunges")
        add("Calf raise", "calf raise", "calf raises", "calves", "calf")
        add("Deadlift", "deadlift", "deadlifts", "dead lift")
    }

    private val word = Regex("[a-z]+")

    private fun norm(s: String) = s.lowercase().replace("-", " ")

    /**
     * The best lift named in [text], or null. Longer phrases win ("incline dumbbell" over "dumbbell"), then earlier ones.
     * A built-in alias resolves to the user's lift whose name contains the built-in name's words; with no such
     * lift it resolves to the built-in name itself (the executor adds that lift).
     */
    fun find(text: String, known: List<String> = emptyList()): ExerciseMatch? {
        val t = norm(text)
        val words = word.findAll(t).toList()
        if (words.isEmpty()) return null
        val phrases = candidates(known)
        var best: Triple<Int, Int, ExerciseMatch>? = null
        for ((phrase, name) in phrases) {
            val parts = phrase.split(' ')
            for (i in 0..words.size - parts.size) {
                if (parts.indices.all { same(words[i + it].value, parts[it]) }) {
                    val m = ExerciseMatch(name, words[i].range.first..words[i + parts.size - 1].range.last)
                    val score = parts.size * 100 + parts.sumOf { it.length }
                    if (best == null || score > best.first || (score == best.first && m.range.first < best.third.range.first)) best = Triple(score, i, m)
                }
            }
        }
        return best?.third
    }

    /** The user's lift (or built-in name) that [spoken] means, for typed or model-supplied names. */
    fun resolve(spoken: String, known: List<String>): String? = find(spoken, known)?.name ?: known.firstOrNull { norm(it) == norm(spoken).trim() }

    private fun candidates(known: List<String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        known.forEach { out += norm(it).trim() to it }
        for ((phrase, builtin) in aliases) {
            val target = if (known.isEmpty()) builtin else matchKnown(builtin, known) ?: builtin
            out += phrase to target
        }
        return out
    }

    /** The user's lift for a built-in [name]: the same name, or one that holds all its words ("Incline dumbbell press"). */
    private fun matchKnown(name: String, known: List<String>): String? {
        val n = norm(name).trim()
        known.firstOrNull { norm(it).trim() == n }?.let { return it }
        val parts = n.split(' ')
        return known.firstOrNull { k -> val kw = norm(k).split(' '); parts.all { p -> kw.any { same(it, p) } } }
            ?: known.firstOrNull { k -> norm(k).split(' ').toSet().let { kw -> kw.size == 1 && kw.first() in parts } }
    }

    /** Equal words, a plural or a one-letter slip on words of five letters or more. */
    private fun same(a: String, b: String): Boolean {
        if (a == b) return true
        if (a == b + "s" || b == a + "s") return true
        if (a.length < 5 || b.length < 5) return false
        return editDistanceAtMostOne(a, b)
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
