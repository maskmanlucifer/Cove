package app.cove.companion.ai.model

/** What the intent parser knows about the user's data. */
data class IntentContext(
    val habits: List<String> = emptyList(),
    val todoCategories: List<String> = emptyList(),
)

/** What one intent request carries; [now] is local `yyyy-MM-ddTHH:mm`. */
data class IntentRequest(
    val transcript: String,
    val context: IntentContext,
    val now: String,
    val zone: String,
)

/** Voice command understood as one or more drafts; the executor decides what to do with them. */
data class ParsedIntents(val intents: List<VoiceIntent>)

/** Non-journal facts for the brief: [facts] for both lines, [name] only for the intro. */
data class BriefInput(val facts: Map<String, String>, val name: String? = null)

/** One brief line request: [kind] is `intro` or `thought`. */
data class BriefRequest(val kind: String, val facts: Map<String, String>)

/** Optional generated lines of the brief; either may be absent. */
data class BriefLines(val intro: String?, val thought: String?)

/** On-device understanding of a journal entry: one calm sentence, a few tags and a [mood] from [MOODS]. */
data class Summary(val sentence: String, val tags: List<String>, val mood: String?) {
    companion object {
        val MOODS = listOf("calm", "good", "tired", "low", "stressed")
    }
}

/** Outcome of "Test connection" for a cloud provider: [ok] and a plain-language [message]. */
data class CloudCheck(val ok: Boolean, val message: String)
