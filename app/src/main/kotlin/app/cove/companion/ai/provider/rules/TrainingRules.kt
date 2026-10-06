package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.ExerciseNames
import app.cove.companion.ai.model.VoiceIntent
import java.time.LocalDate

/**
 * Rules for the workout intents (see `docs/TRAINING.md` for the phrases): planning lifts on a day, changing today's
 * weight, logging sets, a weigh-in and "what is my workout".
 */
internal object TrainingRules {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val weigh = Regex(
        "(?:weigh(?:ed)?[\\s-]*in|weigh myself|log (?:my )?(?:body ?)?weight|(?:my )?(?:body ?)?weight (?:is|was)|i weigh|i weighed)\\s*(?:at|of|is|was|about)?\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|kilos?|kilograms?|lbs?|pounds?)?",
        opts,
    )

    private val nextWorkout = Regex(
        "^(?:what(?:'s| is)(?: my| the)? (?:next |today'?s )?(?:workout|session|training|exercises?)(?: today| next)?|what (?:am i|are we) training(?: today)?|what do i train(?: today)?|" +
            "what(?:'s| is) (?:on )?(?:for )?(?:my )?(?:workout|training) today|(?:my )?(?:next|today'?s) (?:workout|session))\\??$",
        opts,
    )

    private val change = Regex(
        "^(?:change|set|make|update|switch|bump|raise|lower|increase|decrease|put)\\s+(?:my |the |today'?s )?(.+?)\\s+(?:weight\\s+)?(?:to|at|up to|down to)\\s+(\\d+(?:\\.\\d+)?)\\s*" +
            "(kg|kgs|kilos?|kilograms?|lbs?|pounds?)?(?:\\s+(?:for\\s+)?today)?$",
        opts,
    )

    private val planLead = Regex("^(?:plan|schedule|add|put|program|include|set up)\\b", opts)
    private val pastLog = Regex("\\b(?:i did|did|done|logged?|finished|completed|just did|hit|lifted|managed)\\b", opts)
    private val unitWords = Regex("\\b(?:kgs?|kilos?|kilograms?|lbs?|pounds?)\\b", opts)
    private val noise = Regex("\\b(?:plan|schedule|add|put|program|include|set up|please|my|the|a|workout|exercises?|day|this|next)\\b", opts)
    private val template = Regex("\\b(push|pull|legs?)\\s+(?:day|workout|session)\\b", opts)

    /** Push, Pull and Legs as (name, sets, reps); same lifts as the starter templates in the plan editor. */
    private val starters = mapOf(
        "pus" to listOf(Triple("Bench press", 3, 8), Triple("Overhead press", 3, 8), Triple("Incline dumbbell press", 3, 10), Triple("Triceps pushdown", 3, 12)),
        "pul" to listOf(Triple("Barbell row", 3, 8), Triple("Lat pulldown", 3, 10), Triple("Biceps curl", 3, 12)),
        "leg" to listOf(Triple("Squat", 3, 8), Triple("Romanian deadlift", 3, 8), Triple("Leg press", 3, 10), Triple("Calf raise", 3, 12)),
    )

    /** The training intents in clause [c] (numbers already digitized), or null when it is not about training. */
    fun parse(c: String, exercises: List<String>, today: LocalDate): List<VoiceIntent>? {
        if (nextWorkout.containsMatchIn(c)) return listOf(VoiceIntent.QueryNextWorkout)
        val folded = SpokenSets.decimals(c)
        weigh.find(folded)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@let
            val unit = unitOf(m.groupValues[2])
            val kg = if (unit == "lb") value / 2.2046 else value
            if (kg in 20.0..400.0 && !Regex("\\bspent|paid|rs\\b|₹", opts).containsMatchIn(c)) return listOf(VoiceIntent.LogBodyWeight(value, unit))
        }
        change.find(folded)?.let { m ->
            val name = ExerciseNames.find(m.groupValues[1], exercises)?.name
            val w = m.groupValues[2].toDoubleOrNull()
            if (name != null && w != null && w < 1000) return listOf(VoiceIntent.ChangeWeight(name, w, unitOf(m.groupValues[3])))
        }
        planIntents(folded, exercises, today)?.let { return it }
        val parsed = SpokenSets.parse(c, exercises) ?: return null
        val name = parsed.exercise ?: return null
        return listOf(VoiceIntent.LogSets(name, parsed.sets, parsed.unit))
    }

    private fun unitOf(raw: String): String? = when {
        raw.isEmpty() -> null
        raw.startsWith("lb", true) || raw.startsWith("pound", true) -> "lb"
        else -> "kg"
    }

    private fun planIntents(text: String, exercises: List<String>, today: LocalDate): List<VoiceIntent>? {
        if (pastLog.containsMatchIn(text)) return null
        val day = WorkoutDays.find(text, today)
        val lead = planLead.containsMatchIn(text)
        val dayCounts = day != null && (day.date != today || day.range.first == 0)
        if (!lead && !dayCounts) return null
        val date = day?.date ?: today
        val unit = unitOf(unitWords.find(text)?.value.orEmpty())
        var work = text
        if (day != null) work = work.replaceRange(day.range, " ".repeat(day.range.count()))
        val matches = ArrayList<Pair<String, IntRange>>()
        var masked = work
        while (true) {
            val m = ExerciseNames.find(masked, exercises) ?: break
            matches += m.name to m.range
            masked = masked.replaceRange(m.range, " ".repeat(m.range.count()))
        }
        if (matches.isEmpty()) {
            val t = template.find(work) ?: return null
            val starter = starters[t.groupValues[1].lowercase().take(3)] ?: return null
            return starter.map { (n, s, r) -> VoiceIntent.PlanExercise(date, n, null, s, r, null) }
        }
        val sorted = matches.sortedBy { it.second.first }
        return sorted.mapIndexed { i, (name, range) ->
            val end = sorted.getOrNull(i + 1)?.second?.first ?: work.length
            val (w, sets, reps) = numbers(work.substring(range.last + 1, end))
            VoiceIntent.PlanExercise(date, name, w, sets, reps, unit)
        }
    }

    /** Weight, sets and reps read from the words after a lift's name. */
    internal fun numbers(segment: String): Triple<Double?, Int?, Int?> {
        var s = " " + unitWords.replace(segment, " ").replace(noise, " ") + " "
        var weight: Double? = null
        var sets: Int? = null
        var reps: Int? = null
        fun take(re: Regex, f: (MatchResult) -> Unit): Boolean {
            val m = re.find(s) ?: return false
            f(m)
            s = s.removeRange(m.range)
            return true
        }
        take(Regex("(?<![\\d.])(\\d{1,2})\\s*sets?\\s*(?:of|x|by|at|for)?\\s*(\\d{1,3})(?![\\d.])", opts)) { sets = it.groupValues[1].toInt(); reps = it.groupValues[2].toInt() }
        if (sets == null) take(Regex("(?<![\\d.])(\\d{1,2})\\s*(?:x|×|by)\\s*(\\d{1,3})(?![\\d.])", opts)) {
            val a = it.groupValues[1].toInt()
            if (a <= 10) { sets = a; reps = it.groupValues[2].toInt() } else { weight = a.toDouble(); reps = it.groupValues[2].toInt() }
        }
        if (reps == null) take(Regex("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(?:for|@|x)\\s*(\\d{1,3})(?![\\d.])", opts)) { weight = it.groupValues[1].toDouble(); reps = it.groupValues[2].toInt() }
        if (sets == null) take(Regex("(?<![\\d.])(\\d{1,2})\\s*sets?\\b", opts)) { sets = it.groupValues[1].toInt() }
        if (reps == null) take(Regex("(?<![\\d.])(\\d{1,3})\\s*reps?\\b", opts)) { reps = it.groupValues[1].toInt() }
        if (weight == null) take(Regex("(?<![\\d.])(\\d+(?:\\.\\d+)?)(?![\\d.])")) { weight = it.groupValues[1].toDouble() }
        return Triple(weight?.takeIf { it in 0.0..1000.0 }, sets?.takeIf { it in 1..20 }, reps?.takeIf { it in 1..100 })
    }
}
