package app.cove.companion.ai.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The contract for a weight opinion: `{"s":"one sentence"}`, 1 to 200 characters. */
object AdviceSchema {
    private const val MAX = 200
    private val json = Json { isLenient = false }

    /** The sentence in [raw], or null when the reply is not exactly the contract. */
    fun parse(raw: String): String? {
        val obj = runCatching { json.parseToJsonElement(Unfence.of(raw)) as? JsonObject }.getOrNull() ?: return null
        val s = (obj["s"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return null
        return s.takeIf { it.isNotEmpty() && it.length <= MAX }
    }
}
