package app.cove.companion.data.config

import app.cove.companion.ai.AiService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** What "Test connection" found: [ok] and a plain-language [message]; [offerSetupSql] adds a Copy setup SQL button. */
data class TestResult(val ok: Boolean, val message: String, val offerSetupSql: Boolean = false)

/** The "Test connection" checks. Messages never contain keys or raw error text. The Gemini check goes through [ai]. */
class ConnectionTester(private val client: HttpClient, private val ai: AiService, private val timeoutMs: Long = 10_000) {
    /** Reachable, anon key accepted, and Cove's tables present. */
    suspend fun supabase(url: String, anonKey: String): TestResult {
        val base = url.trimEnd('/')
        return try {
            withTimeoutOrNull(timeoutMs) {
                val settings = client.get("$base/auth/v1/settings") { header("apikey", anonKey) }
                val googleOn = googleEnabled(settings.bodyAsText())
                settingsResult(settings.status.value)?.let { return@withTimeoutOrNull it }
                val probe = client.get("$base/rest/v1/settings") {
                    parameter("select", "id")
                    parameter("limit", 1)
                    header("apikey", anonKey)
                }
                tableResult(probe.status.value, probe.bodyAsText(), googleOn)
            } ?: TestResult(false, "Supabase did not answer in time. Check your connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestResult(false, "Cannot reach Supabase. Check your internet connection and the project URL.")
        }
    }

    /** One tiny Gemini call with [apiKey]. */
    suspend fun gemini(apiKey: String, model: String): TestResult =
        ai.testCloud(apiKey, model).let { TestResult(it.ok, it.message) }

    internal companion object {
        private val json = Json { isLenient = true }

        /** A verdict when the auth settings call already shows a problem, else null to carry on. */
        fun settingsResult(status: Int): TestResult? = when {
            status in 200..299 -> null
            status == 401 || status == 403 ->
                TestResult(false, "Supabase rejected the anon key. Copy it again from Project Settings, API (the anon public key).")
            status == 404 -> TestResult(false, "That address does not look like a Supabase project. Check the Project URL.")
            else -> TestResult(false, "Supabase answered with an error ($status). Try again in a minute.")
        }

        /** Verdict for the probe of Cove's `settings` table; [googleOn] adds a hint when sign-in is not enabled. */
        fun tableResult(status: Int, body: String, googleOn: Boolean?): TestResult {
            val missing = status == 404 || body.contains("PGRST205") || body.contains("PGRST106") || body.contains("does not exist")
            return when {
                missing -> TestResult(false, "Connected, but Cove's tables are missing. Run setup.sql in the Supabase SQL editor.", offerSetupSql = true)
                status in 200..299 || body.contains("42501") ->
                    TestResult(
                        true,
                        "Supabase is reachable, the key works and Cove's tables are there." +
                            if (googleOn == false) " Google sign-in is not turned on in Supabase yet." else "",
                    )
                status == 401 || status == 403 -> TestResult(false, "Supabase rejected the anon key. Copy it again from Project Settings, API.")
                else -> TestResult(false, "Supabase answered with an error ($status). Try again in a minute.")
            }
        }

        /** Whether the auth settings JSON says the Google provider is enabled; null when unknown. */
        fun googleEnabled(body: String): Boolean? = runCatching {
            val external = (json.parseToJsonElement(body) as? JsonObject)?.get("external") as? JsonObject
            (external?.get("google") as? JsonPrimitive)?.booleanOrNull
        }.getOrNull()
    }
}
