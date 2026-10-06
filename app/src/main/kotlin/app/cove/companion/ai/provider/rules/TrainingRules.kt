package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.VoiceIntent

/** Rules for the training intents: logging sets, starting a workout, a weigh-in and "what is my next workout". */
internal object TrainingRules {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val start = Regex(
        "^(?:start|begin|kick off|let'?s (?:start|begin|do)|i'?m (?:starting|beginning)|time (?:for|to start))\\s+(?:my |the |today'?s |a |our )?" +
            "(?:(push|pull|legs?)(?:\\s+(?:day|workout|session))?|workout|training|session|gym)(?:\\s+(?:workout|session|day))?$",
        opts,
    )

    private val weigh = Regex(
        "(?:weigh(?:ed)?[\\s-]*in|weigh myself|log (?:my )?(?:body ?)?weight|(?:my )?(?:body ?)?weight (?:is|was)|i weigh|i weighed)\\s*(?:at|of|is|was|about)?\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|kilos?|kilograms?|lbs?|pounds?)?",
        opts,
    )

    private val nextWorkout = Regex(
        "^(?:what(?:'s| is)(?: my| the)? (?:next )?(?:workout|session|training)(?: today| next)?|when(?:'s| is) my next (?:workout|session)|" +
            "what (?:am i|are we) training(?: today)?|what do i train(?: today)?|(?:my )?next (?:workout|session)|what(?:'s| is) today'?s (?:workout|session))\\??$",
        opts,
    )

    /** The training intent in clause [c] (numbers already digitized), or null. */
    fun parse(c: String, exercises: List<String>): VoiceIntent? {
        if (nextWorkout.containsMatchIn(c)) return VoiceIntent.QueryNextWorkout
        start.find(c)?.let { m ->
            val day = m.groupValues[1].lowercase().takeIf { it.isNotEmpty() }?.let { if (it.startsWith("leg")) "Legs" else it.replaceFirstChar { ch -> ch.uppercase() } }
            return VoiceIntent.StartWorkout(day)
        }
        val folded = SpokenSets.decimals(c)
        weigh.find(folded)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@let
            val unit = when {
                m.groupValues[2].startsWith("lb", true) || m.groupValues[2].startsWith("pound", true) -> "lb"
                m.groupValues[2].isNotEmpty() -> "kg"
                else -> null
            }
            val kg = if (unit == "lb") value / 2.2046 else value
            if (kg in 20.0..400.0 && !Regex("\\bspent|paid|rs\\b|₹", opts).containsMatchIn(c)) return VoiceIntent.LogBodyWeight(value, unit)
        }
        val parsed = SpokenSets.parse(c, exercises) ?: return null
        val name = parsed.exercise ?: return null
        return VoiceIntent.LogSets(name, parsed.sets, parsed.unit)
    }
}
