package app.cove.companion.data.config

import app.cove.companion.data.ai.GeminiDirectClient
import app.cove.companion.data.ai.GeminiError
import app.cove.companion.data.ai.GeminiReply
import app.cove.companion.security.Sealer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JWT = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2ln"
private const val SERVICE_JWT = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.c2ln"
private const val KEY = "AIzaSyA-1234567890abcdefghijklmnopqrstu"
private const val CLIENT = "123-abc.apps.googleusercontent.com"

private class XorSealer : Sealer {
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(box: ByteArray) = encrypt(box)
}

class ValidationTest {
    private fun err(f: CredentialField, v: String) = CredentialValidator.check(f, v)

    @Test fun blankIsAlwaysFine() = CredentialField.entries.forEach { assertNull(err(it, "  ")) }

    @Test fun urls() {
        assertNull(err(CredentialField.SupabaseUrl, "https://abcd.supabase.co/"))
        assertNull(err(CredentialField.SupabaseUrl, "https://db.example.com"))
        assertNotNull(err(CredentialField.SupabaseUrl, "http://abcd.supabase.co"))
        assertNotNull(err(CredentialField.SupabaseUrl, "abcd.supabase.co"))
        assertNotNull(err(CredentialField.SupabaseUrl, "https://supabase.com/dashboard/project/x"))
        assertNotNull(err(CredentialField.SupabaseUrl, "https://abcd.supabase.co/rest/v1"))
    }

    @Test fun anonKey() {
        assertNull(err(CredentialField.SupabaseAnonKey, JWT))
        assertNull(err(CredentialField.SupabaseAnonKey, "sb_publishable_abc"))
        assertNotNull(err(CredentialField.SupabaseAnonKey, "hello"))
        assertTrue(err(CredentialField.SupabaseAnonKey, SERVICE_JWT)!!.contains("secret"))
    }

    @Test fun googleAndGemini() {
        assertNull(err(CredentialField.GoogleWebClientId, CLIENT))
        assertNotNull(err(CredentialField.GoogleWebClientId, "123-abc"))
        assertTrue(err(CredentialField.GoogleWebClientId, "GOCSPX-abc")!!.contains("secret"))
        assertNull(err(CredentialField.GeminiApiKey, KEY))
        assertNotNull(err(CredentialField.GeminiApiKey, "sk-123"))
        assertNull(err(CredentialField.GeminiModel, "models/gemini-2.5-flash"))
        assertNotNull(err(CredentialField.GeminiModel, "not a model"))
    }

    @Test fun normalizeTrimsQuotesAndSlash() {
        assertEquals("https://a.supabase.co", CredentialValidator.normalize(CredentialField.SupabaseUrl, " \"https://a.supabase.co/\"\n"))
    }
}

class SetupCodeTest {
    @Test fun roundTrip() {
        val values = mapOf(CredentialField.SupabaseUrl to "https://a.supabase.co", CredentialField.GeminiApiKey to KEY)
        val code = SetupCode.encode(values)
        assertTrue(code.startsWith("cove-setup:1:"))
        assertEquals(SetupCodeResult.Parsed(values), SetupCode.parse("Here you go:\n$code\n"))
    }

    @Test fun acceptsStandardBase64WithWrapping() {
        val json = """{"googleWebClientId":"$CLIENT"}"""
        val std = Base64.getEncoder().encodeToString(json.toByteArray()).chunked(10).joinToString("\n")
        assertEquals(SetupCodeResult.Parsed(mapOf(CredentialField.GoogleWebClientId to CLIENT)), SetupCode.parse("cove-setup:1:$std"))
    }

    @Test fun malformed() {
        listOf("", "hello", "cove-setup:1:!!!", "cove-setup:2:e30", "cove-setup:1:e30", "cove-setup:1:" + Base64.getUrlEncoder().encodeToString("[1]".toByteArray()))
            .forEach { assertTrue(it, SetupCode.parse(it) is SetupCodeResult.Invalid) }
    }

    @Test fun invalidValueRejectsWholeCode() {
        val code = SetupCode.encode(mapOf(CredentialField.GeminiApiKey to "nope", CredentialField.GoogleWebClientId to CLIENT))
        val r = SetupCode.parse(code) as SetupCodeResult.Invalid
        assertTrue(r.message.startsWith("Gemini API key"))
    }
}

class CredentialStoreTest {
    private val file = File.createTempFile("cred", ".bin").also { it.delete() }

    @Test fun persistsEncrypted() {
        val store = CredentialStore(file, XorSealer())
        store.update(mapOf(CredentialField.GeminiApiKey to " $KEY "))
        assertEquals(KEY, store.credentials.value.geminiApiKey)
        assertFalse(String(file.readBytes()).contains(KEY))
        assertEquals(KEY, CredentialStore(file, XorSealer()).credentials.value.geminiApiKey)
    }

    @Test fun devDefaultsOnlyFillBlanks() {
        val store = CredentialStore(file, XorSealer(), Credentials(supabaseUrl = "https://dev.supabase.co", googleWebClientId = "dev"))
        assertEquals("https://dev.supabase.co", store.credentials.value.supabaseUrl)
        store.update(mapOf(CredentialField.SupabaseUrl to "https://mine.supabase.co"))
        assertEquals("https://mine.supabase.co", store.credentials.value.supabaseUrl)
        assertEquals("dev", store.credentials.value.googleWebClientId)
    }

    @Test fun unreadableFileMeansEmpty() {
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(Credentials(), CredentialStore(file, XorSealer()).credentials.value)
    }

    @Test fun clearRemovesFile() {
        val store = CredentialStore(file, XorSealer())
        store.update(mapOf(CredentialField.GeminiApiKey to KEY))
        store.clear()
        assertFalse(file.exists())
    }
}

class ServiceProviderTest {
    private class Fake(val c: Credentials) { var retired = false }

    @Test fun rebuildsOnlyWhenRelevantPartChanges() {
        val flow = MutableStateFlow(Credentials())
        val built = ArrayList<Fake>()
        val p = ServiceProvider(flow, { it.supabaseUrl }, { Fake(it).also(built::add) }, { it.retired = true })
        val first = p.get()
        flow.value = flow.value.copy(geminiApiKey = KEY)
        assertSame(first, p.get())
        flow.value = flow.value.copy(supabaseUrl = "https://a.supabase.co")
        val second = p.get()
        assertNotEquals(first, second)
        assertTrue(first.retired)
        assertFalse(second.retired)
        assertEquals(2, built.size)
    }

    @Test fun blankCredentialsBuildDisabledInstanceWithoutThrowing() {
        val p = ServiceProvider(MutableStateFlow(Credentials()), { it.hasSupabase }, { it.hasSupabase })
        assertFalse(p.get())
    }
}

class GeminiDirectClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val seen = ArrayList<HttpRequestData>()

    private fun reply(text: String) = """{"candidates":[{"content":{"parts":[{"text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}}]}"""

    private fun client(vararg answers: Pair<HttpStatusCode, String>): GeminiDirectClient {
        var i = 0
        val engine = MockEngine { req ->
            seen += req
            val (s, b) = answers[minOf(i++, answers.size - 1)]
            respond(b, s, json)
        }
        return GeminiDirectClient(KEY, "m1", "m2", HttpClient(engine))
    }

    private val ok = """{"intents":[{"type":"set_alarm","time":"06:00"}],"confidence":0.9}"""

    @Test fun buildsRequestWithKeyHeaderOnly() = runBlocking {
        val out = client(HttpStatusCode.OK to reply(ok)).parseIntent("wake me at 6", "2026-10-06T09:00", "Asia/Kolkata", listOf("Home"))
        assertNotNull(out)
        val req = seen.single()
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/m1:generateContent", req.url.toString())
        assertEquals(KEY, req.headers["x-goog-api-key"])
        assertFalse(req.url.toString().contains(KEY))
    }

    @Test fun retriesOnFallbackModelAfterBadOutput() = runBlocking {
        val out = client(HttpStatusCode.OK to reply("not json"), HttpStatusCode.OK to reply(ok)).parseIntent("x", "n", "z", emptyList())
        assertNotNull(out)
        assertEquals(listOf("m1", "m2"), seen.map { it.url.encodedPath.substringAfterLast('/').substringBefore(':') })
    }

    @Test fun invalidKeyDoesNotRetry() = runBlocking {
        val out = client(HttpStatusCode.BadRequest to """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""").parseIntent("x", "n", "z", emptyList())
        assertNull(out)
        assertEquals(1, seen.size)
    }

    @Test fun rejectsUnknownIntentsLowConfidenceAndLongTranscripts() = runBlocking {
        assertNull(GeminiDirectClient.validateIntent("""{"intents":[{"type":"launch"}]}"""))
        assertNull(GeminiDirectClient.validateIntent("""{"intents":[{"type":"undo_last"}],"confidence":0.2}"""))
        assertNull(client(HttpStatusCode.OK to reply(ok)).parseIntent("x".repeat(601), "n", "z", emptyList()))
        assertTrue(seen.isEmpty())
    }

    @Test fun briefLineDropsJournalFacts() = runBlocking {
        val line = client(HttpStatusCode.OK to reply("""{"text":"Good morning."}""")).briefLine("intro", mapOf("weather" to "sun", "journal_text" to "secret"))
        assertEquals("Good morning.", line)
        val body = (seen.single().body as io.ktor.http.content.TextContent).text
        assertTrue(body.contains("sun"))
        assertFalse(body.contains("secret"))
    }

    @Test fun errorMapping() {
        assertEquals(GeminiError.Quota, GeminiDirectClient.mapFailure(429, ""))
        assertEquals(GeminiError.ModelNotFound, GeminiDirectClient.mapFailure(404, ""))
        assertEquals(GeminiError.Server, GeminiDirectClient.mapFailure(503, ""))
        assertEquals(GeminiError.Forbidden, GeminiDirectClient.mapFailure(403, "{}"))
    }

    @Test fun pingResults() = runBlocking {
        assertTrue(ConnectionTester.geminiResult(client(HttpStatusCode.OK to reply("OK")).ping()).ok)
        val bad = ConnectionTester.geminiResult(GeminiReply.Failure(GeminiError.Offline))
        assertFalse(bad.ok)
        assertTrue(bad.message.contains("internet"))
    }
}

class ConnectionTesterTest {
    @Test fun supabaseVerdicts() {
        assertTrue(ConnectionTester.settingsResult(401)!!.message.contains("anon key"))
        assertNull(ConnectionTester.settingsResult(200))
        val missing = ConnectionTester.tableResult(404, """{"code":"PGRST205"}""", true)
        assertTrue(missing.offerSetupSql && missing.message.contains("setup.sql"))
        assertTrue(ConnectionTester.tableResult(200, "[]", true).ok)
        assertTrue(ConnectionTester.tableResult(200, "[]", false).message.contains("Google sign-in"))
        assertEquals(false, ConnectionTester.googleEnabled("""{"external":{"google":false}}"""))
    }

    @Test fun supabaseEndToEndWithMockEngine() = runBlocking {
        val engine = MockEngine { req ->
            val h = headersOf(HttpHeaders.ContentType, "application/json")
            if (req.url.encodedPath.endsWith("/settings") && req.url.encodedPath.contains("auth")) respond("""{"external":{"google":true}}""", HttpStatusCode.OK, h)
            else respond("""{"code":"PGRST205"}""", HttpStatusCode.NotFound, h)
        }
        val r = ConnectionTester(HttpClient(engine)).supabase("https://a.supabase.co", JWT)
        assertTrue(r.offerSetupSql)
    }
}
