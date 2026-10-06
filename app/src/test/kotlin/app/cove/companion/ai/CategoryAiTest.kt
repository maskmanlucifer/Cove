package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.prompt.CategoryPrompt
import app.cove.companion.ai.provider.CategoryProvider
import app.cove.companion.ai.provider.cloud.CloudGateway
import app.cove.companion.ai.provider.cloud.CloudProvider
import app.cove.companion.ai.provider.ondevice.NanoProvider
import app.cove.companion.ai.provider.ondevice.NanoReply
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.TypedSpeechProvider
import app.cove.companion.ai.schema.CategorySchema
import app.cove.companion.core.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryAiTest {
    private val cats = listOf("Food", "Transport", "Gifts")
    private var foreground = true
    private var online = true

    private class FakeCategoryProvider(id: String, location: Location, vararg script: AiResult<List<CategorySuggestion>>, state: Availability = Availability.Available) :
        FakeProvider<List<CategorySuggestion>>(id, location, state, script.toList()), CategoryProvider {
        val requests = mutableListOf<CategoryRequest>()
        override suspend fun suggest(request: CategoryRequest): AiResult<List<CategorySuggestion>> { requests += request; return answer() }
    }

    private class CategoryGateway(var reply: String?, override val enabled: Boolean = true) : CloudGateway {
        val asked = mutableListOf<String>()
        override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? = null
        override suspend fun suggestCategories(system: String, user: String): String? { asked += user; return reply }
    }

    private fun service(vararg list: CategoryProvider): AiService {
        val providers = AiProviders(category = list.toList())
        return DefaultAiService(
            AiRouter(providers, AiPolicy({ foreground }, { online }), backoffMs = 1, callTimeoutMs = 500), providers,
            TypedSpeechProvider(), RuleParser(Clock.System), Clock.System,
        ) { _, _ -> app.cove.companion.ai.model.CloudCheck(true, "ok") }
    }

    private val one = listOf(CategorySuggestion(0, "Food"))

    @Test fun promptNeverContainsAmountsOrDigits() {
        val req = CategoryRequest(listOf("Blinkit ₹450", "uber 120 rs", "swiggy 1,250.50", "Rs. 99 chai", "birthday gift 2k"), cats)
        val user = CategoryPrompt.user(req)
        assertFalse(user, Regex("\\d").containsMatchIn(user))
        assertFalse(user.contains("₹"))
        assertTrue(user.contains("Blinkit") && user.contains("birthday gift"))
        assertEquals(setOf("categories", "items"), kotlinx.serialization.json.Json.parseToJsonElement(user).let { (it as kotlinx.serialization.json.JsonObject).keys })
    }

    @Test fun promptCapsNoteLengthAndFitsNanoAtFullBatch() {
        val long = "x".repeat(500)
        assertEquals(80, CategoryPrompt.scrub(long).length)
        val full = CategoryRequest(List(40) { "a fairly long expense note number $it".replace(Regex("\\d"), "") }, List(12) { "Category$it".replace(Regex("\\d"), "") })
        assertTrue(CategoryPrompt.nano(full) != null)
    }

    @Test fun schemaAcceptsOnlyTheExactContract() {
        assertEquals(listOf(CategorySuggestion(0, "Food"), CategorySuggestion(2, "Gifts")), CategorySchema.parse("""{"a":[0,-1,2]}""", 3, cats))
        assertEquals(listOf(CategorySuggestion(1, "Transport")), CategorySchema.parse("```json\n{\"a\":[-1,1]}\n```", 2, cats))
        assertEquals(null, CategorySchema.parse("""{"a":[0,1]}""", 3, cats))
        assertEquals(null, CategorySchema.parse("""{"a":[3]}""", 1, cats))
        assertEquals(null, CategorySchema.parse("""{"a":[-2]}""", 1, cats))
        assertEquals(null, CategorySchema.parse("""{"a":["Food"]}""", 1, cats))
        assertEquals(null, CategorySchema.parse("""{"a":[0.5]}""", 1, cats))
        assertEquals(null, CategorySchema.parse("""{"b":[0]}""", 1, cats))
        assertEquals(null, CategorySchema.parse("not json", 1, cats))
    }

    @Test fun batchesAreAtMostForty() {
        assertEquals(listOf(40, 40, 15), CategoryRequest.batches(List(95) { it }).map { it.size })
        assertEquals(emptyList<List<Int>>(), CategoryRequest.batches(emptyList<Int>()))
    }

    @Test fun serviceRefusesOversizedOrEmptyBatches() = runBlocking {
        val p = FakeCategoryProvider("nano", Location.Native, ok(one))
        val s = service(p)
        assertTrue(s.suggestCategories(List(41) { "x" }, cats) is AiResult.Failed)
        assertTrue(s.suggestCategories(emptyList(), cats) is AiResult.Failed)
        assertTrue(s.suggestCategories(listOf("x"), emptyList()) is AiResult.Failed)
        assertEquals(0, p.calls)
        assertTrue(s.suggestCategories(List(40) { "x" }, cats) is AiResult.Ok)
        assertEquals(1, p.calls)
    }

    @Test fun nanoFirstWhenForegroundThenCloud() = runBlocking {
        val nano = FakeCategoryProvider("nano", Location.Native, ok(one, "nano"))
        val cloud = FakeCategoryProvider("gemini", Location.Cloud, ok(one, "gemini", Location.Cloud))
        val s = service(nano, cloud)
        assertEquals("nano", (s.suggestCategories(listOf("lunch"), cats) as AiResult.Ok).source.id)
        foreground = false
        assertEquals("gemini", (s.suggestCategories(listOf("lunch"), cats) as AiResult.Ok).source.id)
        assertEquals(1, nano.calls)
    }

    @Test fun fallsThroughWhenNanoFailsAndNeverRunsOfflineOrWithoutKey() = runBlocking {
        val nano = FakeCategoryProvider("nano", Location.Native, fail(AiError.InvalidOutput, "nano"))
        val cloud = FakeCategoryProvider("gemini", Location.Cloud, ok(one, "gemini", Location.Cloud))
        assertEquals("gemini", (service(nano, cloud).suggestCategories(listOf("a"), cats) as AiResult.Ok).source.id)
        online = false
        val offline = service(cloud).suggestCategories(listOf("a"), cats)
        assertEquals(AiError.Offline, (offline as AiResult.Failed).error)
        online = true
        val noKey = service(FakeCategoryProvider("gemini", Location.Cloud, state = Availability.NeedsConfig("API key"))).suggestCategories(listOf("a"), cats)
        assertEquals(AiError.NeedsConfig("API key"), (noKey as AiResult.Failed).error)
    }

    @Test fun cloudProviderSendsOnlyScrubbedNotesAndValidatesTheReply() = runBlocking {
        val gw = CategoryGateway("""{"a":[0,2]}""")
        val p = CloudProvider("gemini", "API key") { gw }
        val r = p.suggest(CategoryRequest(listOf("blinkit 450", "gift 800"), cats)) as AiResult.Ok
        assertEquals(listOf(CategorySuggestion(0, "Food"), CategorySuggestion(1, "Gifts")), r.value)
        assertFalse(Regex("\\d").containsMatchIn(gw.asked.single()))
        gw.reply = """{"a":[0]}"""
        assertEquals(AiError.InvalidOutput, (p.suggest(CategoryRequest(listOf("a", "b"), cats)) as AiResult.Failed).error)
        gw.reply = null
        assertTrue((p.suggest(CategoryRequest(listOf("a"), cats)) as AiResult.Failed).error is AiError.Unavailable)
    }

    @Test fun nanoProviderParsesAndSendsNoDigits() = runBlocking {
        val client = FakeNanoClient(replies = arrayOf(NanoReply.Text("""{"a":[1]}""")))
        val r = NanoProvider(client).suggest(CategoryRequest(listOf("uber 120"), cats)) as AiResult.Ok
        assertEquals(listOf(CategorySuggestion(0, "Transport")), r.value)
        assertFalse(client.prompts.single().substringAfter("{\"categories\"").contains(Regex("\\d")))
    }

    @Test fun nanoInvalidReplyIsInvalidOutput() = runBlocking {
        val r = NanoProvider(FakeNanoClient(replies = arrayOf(NanoReply.Text("sure!")))).suggest(CategoryRequest(listOf("x"), cats))
        assertEquals(AiError.InvalidOutput, (r as AiResult.Failed).error)
    }
}
