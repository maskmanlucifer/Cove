package app.cove.companion.ai.schema

import app.cove.companion.ai.model.CategorySuggestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The contract for category suggestions: `{"a":[i0,i1,...]}`, one integer in -1 until categoryCount per note. */
object CategorySchema {
    private val json = Json { isLenient = false }

    /**
     * The suggestions in [raw] for [noteCount] notes and [categories], or null when the reply is not exactly
     * the contract (wrong length, non-integers, out-of-range indexes). Notes answered -1 are left out.
     */
    fun parse(raw: String, noteCount: Int, categories: List<String>): List<CategorySuggestion>? {
        val obj = runCatching { json.parseToJsonElement(Unfence.of(raw)) as? JsonObject }.getOrNull() ?: return null
        val arr = obj["a"] as? JsonArray ?: return null
        if (arr.size != noteCount) return null
        val out = ArrayList<CategorySuggestion>()
        for ((i, e) in arr.withIndex()) {
            val p = e as? JsonPrimitive ?: return null
            if (p.isString) return null
            val n = p.content.toIntOrNull() ?: return null
            if (n < -1 || n >= categories.size) return null
            if (n >= 0) out += CategorySuggestion(i, categories[n])
        }
        return out
    }
}
