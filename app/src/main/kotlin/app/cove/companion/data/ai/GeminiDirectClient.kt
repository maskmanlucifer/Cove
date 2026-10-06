package app.cove.companion.data.ai

import app.cove.companion.feature.voice.intent.IntentJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** Why a Gemini call failed, in terms a person can act on. */
enum class GeminiError { InvalidKey, Forbidden, Quota, ModelNotFound, BadRequest, Offline, Timeout, Server, BadResponse }

/** Result of one Gemini call. */
sealed interface GeminiReply {
    data class Text(val text: String) : GeminiReply
    data class Failure(val error: GeminiError) : GeminiReply
}

/**
 * [AiGateway] that calls Gemini straight from the phone with the user's own API key (sent only in the
 * `x-goog-api-key` header). Replies are requested as JSON and validated with the same rules the legacy
 * `ai-gateway` Edge Function applies; a failed or invalid first answer is retried once on [fallbackModel].
 * Only short, non-journal text is ever sent: [IntentParser][app.cove.companion.feature.voice.intent.IntentParser]
 * keeps journal notes and long transcripts on the device, and this client drops them again as a second guard.
 */
class GeminiDirectClient(
    private val apiKey: String,
    private val model: String,
    private val fallbackModel: String,
    private val client: HttpClient,
    private val timeoutMs: Long = 4_000,
) : AiGateway {
    override val enabled: Boolean get() = apiKey.isNotBlank()

    override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? {
        if (!enabled || transcript.isBlank() || transcript.length > MAX_TRANSCRIPT) return null
        val system = IntentJson.systemPrompt(now, todoCategories) + "\nTime zone: $zone."
        return withFallback(system, "Command: $transcript", ::validateIntent)
    }

    override suspend fun briefLine(kind: String, facts: Map<String, String>): String? {
        if (!enabled) return null
        val safe = facts.filterKeys { !it.contains("journal", ignoreCase = true) }
        val system = "You write one short, calm line for a morning brief from the supplied facts. " +
            "kind intro: one warm sentence greeting the person. kind thought: one gentle thing to think about. " +
            "Never invent facts. Reply with JSON only: {\"text\":\"...\"}."
        val user = buildJsonObject {
            put("kind", kind)
            put("facts", buildJsonObject { safe.forEach { (k, v) -> put(k, v) } })
        }.toString()
        return withFallback(system, user, ::validateLine)
    }

    /** One tiny call on the primary model with a longer timeout, for "Test connection". */
    suspend fun ping(): GeminiReply =
        generate(model, "Reply with the single word OK.", "Say OK.", json = false, maxTokens = 16, timeout = PING_TIMEOUT_MS)

    private suspend fun <R : Any> withFallback(system: String, user: String, validate: (String) -> R?): R? {
        for (m in listOf(model, fallbackModel).filter { it.isNotBlank() }.distinct()) {
            when (val reply = generate(m, system, user)) {
                is GeminiReply.Text -> validate(reply.text)?.let { return it }
                is GeminiReply.Failure -> if (reply.error == GeminiError.InvalidKey || reply.error == GeminiError.Offline) return null
            }
        }
        return null
    }

    private suspend fun generate(
        model: String,
        system: String,
        user: String,
        json: Boolean = true,
        maxTokens: Int? = null,
        timeout: Long = timeoutMs,
    ): GeminiReply {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.1)
                if (json) put("responseMimeType", "application/json")
                if (maxTokens != null) put("maxOutputTokens", maxTokens)
            })
        }
        return try {
            withTimeoutOrNull(timeout) {
                val response = client.post("$BASE/$model:generateContent") {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                val text = response.bodyAsText()
                if (response.status.value in 200..299) extractText(text) else GeminiReply.Failure(mapFailure(response.status.value, text))
            } ?: GeminiReply.Failure(GeminiError.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            GeminiReply.Failure(GeminiError.Offline)
        } catch (e: Exception) {
            GeminiReply.Failure(GeminiError.BadResponse)
        }
    }

    internal companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MAX_TRANSCRIPT = 600
        const val PING_TIMEOUT_MS = 10_000L
        private val json = Json { isLenient = false }
        private val intentTypes = setOf(
            "set_alarm", "change_alarm", "add_todo", "add_reminder", "log_expense",
            "log_habit", "journal_note", "query_next", "undo_last",
        )

        /** Maps an HTTP failure to a [GeminiError]; the key is never part of [body] handling output. */
        fun mapFailure(status: Int, body: String): GeminiError = when {
            body.contains("API_KEY_INVALID") || body.contains("API key not valid") || status == 401 -> GeminiError.InvalidKey
            status == 403 -> GeminiError.Forbidden
            status == 404 -> GeminiError.ModelNotFound
            status == 429 -> GeminiError.Quota
            status in 500..599 -> GeminiError.Server
            else -> GeminiError.BadRequest
        }

        /** Pulls the first candidate's text out of a `generateContent` response. */
        fun extractText(body: String): GeminiReply {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val parts = ((((root?.get("candidates") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
            val text = parts?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.content }?.joinToString("")?.trim()
            return if (text.isNullOrEmpty()) GeminiReply.Failure(GeminiError.BadResponse) else GeminiReply.Text(text)
        }

        private fun unfence(s: String) = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        /** Same checks as the Edge Function: at most five known intents; unclear or low-confidence answers are dropped. */
        fun validateIntent(raw: String): String? {
            val obj = runCatching { json.parseToJsonElement(unfence(raw)) as? JsonObject }.getOrNull() ?: return null
            val intents = obj["intents"] as? JsonArray ?: return null
            if (intents.isEmpty() || intents.size > 5) return null
            if (intents.any { i -> ((i as? JsonObject)?.get("type") as? JsonPrimitive)?.content !in intentTypes }) return null
            val confidence = ((obj["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.8).coerceIn(0.0, 1.0)
            if (confidence < 0.5) return null
            return buildJsonObject {
                put("intents", intents)
                put("confidence", confidence)
            }.toString()
        }

        /** One short line from `{"text": "..."}`. */
        fun validateLine(raw: String): String? {
            val obj = runCatching { json.parseToJsonElement(unfence(raw)) as? JsonObject }.getOrNull() ?: return null
            val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            return text?.takeIf { it.isNotEmpty() && it.length < 280 }
        }
    }
}
