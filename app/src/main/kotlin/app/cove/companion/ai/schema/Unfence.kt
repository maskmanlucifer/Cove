package app.cove.companion.ai.schema

/** Models sometimes wrap JSON in a Markdown code fence; this removes it. */
internal object Unfence {
    fun of(s: String) = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
