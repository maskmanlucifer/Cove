package app.cove.companion.ai.model

import app.cove.companion.data.memory.MemoryText

/** Plain notes: what nothing else in the app understood but reads like something worth keeping. Pure, shared by the rules and the Voice screen. */
object MemoryNotes {
    private val questionLead = Regex("^(?:what|where|when|who|whom|why|how|which|can|could|should|would|is|are|am|do|does|did|will)\\b", RegexOption.IGNORE_CASE)

    /**
     * Whether [text], which nothing else understood, reads as a statement worth keeping rather than a question or noise:
     * at least three words and not a question.
     */
    fun looksLikeNote(text: String): Boolean {
        val t = text.trim()
        return t.split(Regex("\\s+")).size >= 3 && !t.endsWith("?") && !questionLead.containsMatchIn(t)
    }

    /** A plain note: the first few meaningful words become its subject so a later note about the same thing replaces it. */
    fun note(text: String): VoiceIntent.Remember {
        val body = text.trim().trimEnd('.', '!', ' ').replaceFirstChar { it.uppercase() }
        val subject = MemoryText.keywords(body).take(3).joinToString(" ").ifEmpty { "note" }
        return VoiceIntent.Remember(body, subject, kind = "note")
    }
}
