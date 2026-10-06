package app.cove.companion.ai.provider.cloud

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
 * Where voice commands and brief lines go when the on-device layers cannot answer: [GeminiDirectClient] by default,
 * or the legacy Edge Function client. Never send journal content through it.
 */
interface CloudGateway {
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

    /**
     * Runs the category prompt ([system], [user] as built by `CategoryPrompt`: note text and category names only).
     *
     * @return the raw JSON reply to validate, or null when unsupported, offline or on any error.
     */
    suspend fun suggestCategories(system: String, user: String): String? = null

    /** Runs the workout advice prompt ([system], [user] as built by `AdvicePrompt`); the raw JSON reply, or null. */
    suspend fun adviseWorkout(system: String, user: String): String? = null
}

/**
 * Legacy mode: client of the optional Supabase Edge Function (see "Advanced: server-side key" in `docs/SETUP.md`).
 * A blank URL disables it and [tokenProvider] supplies the signed-in user token the function verifies.
 */
class EdgeFunctionGateway(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: HttpClient = HttpClient(OkHttp),
    private val timeoutMs: Long = 6_000,
    private val tokenProvider: suspend () -> String? = { null },
) : CloudGateway {
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

/** Gateway used when no AI service is set up: always disabled. */
object NoCloudGateway : CloudGateway {
    override val enabled: Boolean get() = false
    override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? = null
}
