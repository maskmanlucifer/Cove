package app.cove.companion.ai.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The contract for same-payment answers: `{"a":[1,0,...]}`, exactly one 0 or 1 per pair. */
object SamePaymentSchema {
    private val json = Json { isLenient = false }

    /** One answer per pair (true = same payment), or null when [raw] is not exactly the contract for [count] pairs. */
    fun parse(raw: String, count: Int): List<Boolean>? {
        val obj = runCatching { json.parseToJsonElement(Unfence.of(raw)) as? JsonObject }.getOrNull() ?: return null
        val arr = obj["a"] as? JsonArray ?: return null
        if (arr.size != count) return null
        return arr.map { e ->
            val p = e as? JsonPrimitive ?: return null
            if (p.isString) return null
            when (p.content) { "1" -> true; "0" -> false; else -> return null }
        }
    }
}
