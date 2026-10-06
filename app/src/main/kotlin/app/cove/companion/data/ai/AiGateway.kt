package app.cove.companion.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Client of the Supabase Edge Function `ai-gateway` (PLAN 6b). Never send journal content through it.
 */
interface AiGateway {
    /** False when no backend is configured; callers skip the cloud layer entirely. */
    val enabled: Boolean

    /**
     * Asks the gateway to parse a voice command ([transcript]) given the local [now] and category names.
     *
     * @return the `{"intents":[...]}` JSON text to validate, or null on error, timeout or low confidence.
     */
    suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String?

    /**
     * Asks for one short line of brief copy: [kind] is `intro` or `thought`, [facts] are non-journal facts only.
     *
     * @return the line, or null when disabled, offline or on any error.
     */
    suspend fun briefLine(kind: String, facts: Map<String, String>): String? = null
}

/** Ktor implementation; URL and key come from `BuildConfig`, a blank URL disables it, and [tokenProvider] supplies the signed-in user token the gateway verifies. */
class KtorAiGateway(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: HttpClient = HttpClient(OkHttp),
    private val timeoutMs: Long = 6_000,
    private val tokenProvider: suspend () -> String? = { null },
) : AiGateway {
    override val enabled: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? {
        if (!enabled) return null
        val body = buildJsonObject {
            put("task", "intent")
            put("payload", buildJsonObject {
                put("transcript", transcript)
                put("now", now)
                put("timezone", zone)
                put("categories", kotlinx.serialization.json.JsonArray(todoCategories.map(::JsonPrimitive)))
            })
        }
        return try {
            withTimeoutOrNull(timeoutMs) {
                val response = client.post(baseUrl.trimEnd('/') + "/functions/v1/ai-gateway") {
                    header("apikey", anonKey)
                    header(HttpHeaders.Authorization, "Bearer ${tokenProvider() ?: anonKey}")
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                if (response.status.isSuccess()) accept(response.bodyAsText()) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun briefLine(kind: String, facts: Map<String, String>): String? {
        if (!enabled) return null
        val body = buildJsonObject {
            put("task", "brief")
            put("payload", buildJsonObject {
                put("kind", kind)
                put("facts", buildJsonObject { facts.forEach { (k, v) -> put(k, v) } })
            })
        }
        return try {
            withTimeoutOrNull(timeoutMs) {
                val response = client.post(baseUrl.trimEnd('/') + "/functions/v1/ai-gateway") {
                    header("apikey", anonKey)
                    header(HttpHeaders.Authorization, "Bearer $anonKey")
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                if (!response.status.isSuccess()) return@withTimeoutOrNull null
                val obj = runCatching { Json.parseToJsonElement(response.bodyAsText()) as? JsonObject }.getOrNull()
                (obj?.get("text") as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && obj.containsKey("error").not() && it.length < 280 }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Drops error and low-confidence replies; the rest is validated by the caller. */
    private fun accept(text: String): String? {
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        if (obj.containsKey("error")) return null
        val confidence = (obj["confidence"] as? JsonPrimitive)?.doubleOrNull
        return if (confidence != null && confidence < 0.5) null else text
    }
}
