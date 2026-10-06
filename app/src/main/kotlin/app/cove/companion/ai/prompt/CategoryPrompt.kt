package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.CategoryRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Prompt for filing a batch of expense notes under the user's categories. Only note text and category names are
 * sent: amounts and currency are scrubbed from notes, and notes are identified by position alone.
 * The reply is `{"a":[2,0,-1,...]}`: one category index per note (-1 = none fits), short enough for Nano's output cap.
 */
object CategoryPrompt {
    const val SYSTEM = "You file expense notes under categories. The input has \"categories\" (a list) and \"items\" (short notes). " +
        "For each item, in order, answer the index of the best category, or -1 when none clearly fits. " +
        "Reply with JSON only: {\"a\":[...]} with exactly one integer per item."

    private const val MAX_NOTE = 80
    private val amounts = Regex("(?i)(?:₹|\\$|rs\\.?|inr|rupees?)?\\s*\\d[\\d,.]*\\s*(?:k|rs|rupees?)?\\b")
    private val spaces = Regex("\\s+")

    /** [note] without amounts, trimmed and capped; this is the only form of a note that leaves the app. */
    fun scrub(note: String): String =
        note.replace(amounts, " ").replace("₹", " ").replace(spaces, " ").trim().take(MAX_NOTE)

    /** The user turn as JSON. */
    fun user(request: CategoryRequest): String = buildJsonObject {
        put("categories", JsonArray(request.categories.map { JsonPrimitive(it.trim().take(40)) }))
        put("items", JsonArray(request.notes.map { JsonPrimitive(scrub(it)) }))
    }.toString()

    /** One prompt string for Nano, or null when it would not fit. */
    fun nano(request: CategoryRequest): String? =
        "$SYSTEM\n\n${user(request)}".takeIf { it.length <= PromptLimits.NANO_MAX_PROMPT_CHARS }
}
