package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.MemoryNotes
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.data.memory.MemoryPolicy

/**
 * Rules for things worth keeping ("I parked on level 3, pillar B", "my passport is in the blue folder") and for asking
 * them back ("where did I park?"). Questions are checked early because they are unambiguous; statements only after every
 * in-app command (alarms, to-dos, money, habits, workouts) has had its turn, so a command is never taken for a note.
 */
object MemoryRules {
    private val o = setOf(RegexOption.IGNORE_CASE)
    private const val ARTICLE = "(?:my |the |our |a |an )?"
    private const val PLACE = "(in|on|at|under|inside|near|behind|beside|with|outside)"
    private const val VEHICLE = "(?:car|bike|scooter|vehicle)"

    private val parkQuestion = Regex("^where (?:did i|have i|do i|had i) park(?:ed)?(?: (?:my|the) $VEHICLE)?\\??$", o)
    private val whereDid = Regex("^where (?:did i|do i|have i|had i) (?:put|keep|kept|leave|left|place|placed|store|stored|save|saved|hide|hid) $ARTICLE(.+?)\\??$", o)
    private val whereIs = Regex("^where(?:'s| is| are) $ARTICLE(.+?)\\??$", o)
    private val about = Regex("^(?:what did i (?:say|tell you|note|save)|what do you (?:know|remember)|do you remember) about $ARTICLE(.+?)\\??$", o)
    private val remembered = Regex("^do you remember $ARTICLE(.+?)\\??$", o)

    private val explicit = Regex("^(?:remember|note|make a note|keep in mind)(?: that)?\\s+(?!to\\b)(.+)$", o)
    private val parked = Regex("^(?:i\\s+)?(?:just\\s+)?parked\\s+(.+)$", o)
    private val vehicleAt = Regex("^(?:my |the )?($VEHICLE)\\s+(?:is |was )?(?:parked\\s+)?((?:at|on|in|near|behind|beside|outside)\\s+.+)$", o)
    private val put = Regex("^(?:i\\s+)?(?:just\\s+)?(?:put|left|kept|keep|placed|stored|hid)\\s+$ARTICLE(.+?)\\s+$PLACE\\s+(.+)$", o)
    private val isIn = Regex("^(?:my|the|our)\\s+(.+?)\\s+(?:is|are|was|were)\\s+$PLACE\\s+(.+)$", o)
    private val timeLike = Regex("^(?:\\d|tomorrow|today|tonight|noon|midnight|monday|tuesday|wednesday|thursday|friday|saturday|sunday)", o)
    private val vehicleLead = Regex("^(?:my |the )?$VEHICLE\\s+", o)

    /** "Where did I park?" and its relatives; null when [c] is not such a question. */
    fun question(c: String): VoiceIntent? {
        if (parkQuestion.containsMatchIn(c)) return VoiceIntent.Recall("car park")
        for (r in listOf(whereDid, about, remembered, whereIs)) {
            r.find(c)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return VoiceIntent.Recall(it) }
        }
        return null
    }

    /** A statement that says where something is, or an explicit "remember ..."; null for anything else. */
    fun statement(c: String): VoiceIntent.Remember? {
        explicit.find(c)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return MemoryNotes.note(it) }
        parked.find(c)?.let { m ->
            val detail = m.groupValues[1].trim().replace(vehicleLead, "").trim()
            return place(c, "car", detail)
        }
        vehicleAt.find(c)?.let { m -> return place(c, m.groupValues[1].lowercase(), m.groupValues[2].trim()) }
        put.find(c)?.let { m -> return place(c, tidy(m.groupValues[1]), "${m.groupValues[2].lowercase()} ${m.groupValues[3].trim()}") }
        isIn.find(c)?.let { m ->
            val detail = "${m.groupValues[2].lowercase()} ${m.groupValues[3].trim()}"
            if (!timeLike.containsMatchIn(m.groupValues[3].trim())) return place(c, tidy(m.groupValues[1]), detail)
        }
        return null
    }

    private fun place(original: String, subject: String, detail: String): VoiceIntent.Remember {
        val s = subject.lowercase()
        val sentence = original.trim().trimEnd('.', '!', ' ').replaceFirstChar { it.uppercase() }
        return VoiceIntent.Remember(sentence, s, detail.trimEnd('.', '!', ' '), "place", MemoryPolicy.keepFor(s))
    }

    private fun tidy(subject: String) = subject.trim().lowercase().take(40)
}
