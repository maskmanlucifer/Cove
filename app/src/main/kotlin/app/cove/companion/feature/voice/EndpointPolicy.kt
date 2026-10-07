package app.cove.companion.feature.voice

/**
 * Decides how long a pause after speech may last before Cove stops waiting and understands what it heard,
 * so the user never has to tap Done. A sentence that sounds unfinished earns more patience than one that sounds complete.
 */
object EndpointPolicy {
    /** Pause allowed after a sentence that sounds complete ("... at 6 pm", "... 250 for lunch"). */
    const val COMPLETE_MS = 1_500L

    /** Pause allowed after speech that could go either way. */
    const val DEFAULT_MS = 2_200L

    /** Pause allowed after a sentence that stops mid-thought ("remind me to", "add milk and"). */
    const val UNFINISHED_MS = 4_500L

    /** A new recognizer run always gets at least this long to hear the user resume, however slowly it started. */
    const val MIN_LISTEN_MS = 1_200L

    private val dangling = setOf(
        "and", "or", "but", "then", "also", "to", "at", "for", "on", "in", "by", "from", "about", "with", "of", "the", "a", "an",
        "my", "me", "remind", "add", "set", "log", "note", "after", "before", "every", "each", "next", "this", "that", "plus", "um", "uh",
    )
    private val closing = setOf("am", "pm", "today", "tomorrow", "tonight", "morning", "evening", "rupees", "rs", "rupee", "done", "please")

    /** Allowed pause, in milliseconds, after [heard] before the dictation is considered finished. */
    fun graceMs(heard: String): Long {
        val text = heard.trim().lowercase()
        if (text.isEmpty()) return DEFAULT_MS
        if (text.endsWith(",")) return UNFINISHED_MS
        val last = text.trimEnd('.', '!', '?').substringAfterLast(' ')
        return when {
            last in dangling -> UNFINISHED_MS
            last.any { it.isDigit() } || last in closing || text.endsWith(".") -> COMPLETE_MS
            else -> DEFAULT_MS
        }
    }
}
