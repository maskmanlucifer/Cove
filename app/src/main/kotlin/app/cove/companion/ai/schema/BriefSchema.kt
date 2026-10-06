package app.cove.companion.ai.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The contract for brief lines: `{"text": "..."}`, one sentence under [MAX_CHARS]. */
object BriefSchema {
    const val MAX_CHARS = 280
    private val json = Json { isLenient = false }

    /** The line from [raw], or null when it is not valid or too long. */
    fun validateLine(raw: String): String? {
        val obj = runCatching { json.parseToJsonElement(Unfence.of(raw)) as? JsonObject }.getOrNull() ?: return null
        val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        return text?.takeIf { it.isNotEmpty() && it.length < MAX_CHARS }
    }
}
