package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.provider.ondevice.NanoFailure
import app.cove.companion.ai.provider.ondevice.NanoProvider
import app.cove.companion.ai.provider.ondevice.NanoProvider.Companion.toError
import app.cove.companion.ai.provider.ondevice.NanoReply
import app.cove.companion.ai.provider.ondevice.NanoStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NanoProviderTest {
    private val request = IntentRequest("walk the dog", IntentContext(todoCategories = listOf("Home")), "2026-10-06T10:35", "UTC")
    private val todoJson = """{"intents":[{"type":"add_todo","items":[{"title":"Walk the dog","category":null}]}]}"""

    @Test fun statusMapsToAvailabilityWithReasons() = runBlocking {
        fun availability(s: NanoStatus) = runBlocking { NanoProvider(FakeNanoClient(s)).availability() }
        assertEquals(Availability.Available, availability(NanoStatus.Available))
        assertEquals(Availability.Unavailable("Gemini Nano unavailable on this device"), availability(NanoStatus.Unavailable))
        assertTrue((availability(NanoStatus.Downloadable) as Availability.Unavailable).reason.contains("not downloaded"))
        assertTrue((availability(NanoStatus.Downloading) as Availability.Unavailable).reason.contains("downloading"))
    }

    @Test fun failuresMapToTypedErrors() {
        assertEquals(AiError.Busy, NanoFailure.Busy.toError())
        assertEquals(AiError.RateLimited, NanoFailure.QuotaExceeded.toError())
        assertEquals(AiError.NeedsForeground, NanoFailure.BackgroundBlocked.toError())
        assertTrue(NanoFailure.NotAvailable.toError() is AiError.Unavailable)
    }

    @Test fun parsesValidIntentAndTagsProvenance() = runBlocking {
        val r = NanoProvider(FakeNanoClient(NanoStatus.Available, NanoReply.Text(todoJson))).parse(request) as AiResult.Ok
        assertEquals("gemini-nano/native", r.source.toString())
        assertEquals(Location.Native, r.source.location)
        assertEquals(1, r.value.intents.size)
    }

    @Test fun invalidOrEmptyIntentIsInvalidOutput() = runBlocking {
        listOf("garbage", """{"intents":[]}""").forEach {
            val r = NanoProvider(FakeNanoClient(NanoStatus.Available, NanoReply.Text(it))).parse(request)
            assertEquals(it, AiError.InvalidOutput, (r as AiResult.Failed).error)
        }
    }

    @Test fun mlKitBusyBecomesBusy() = runBlocking {
        val r = NanoProvider(FakeNanoClient(NanoStatus.Available, NanoReply.Failure(NanoFailure.Busy))).parse(request)
        assertEquals(AiError.Busy, (r as AiResult.Failed).error)
    }

    @Test fun nanoBusyIsRetriedOnceByTheRouterThenCloudIsUsed() = runBlocking {
        val client = FakeNanoClient(NanoStatus.Available, NanoReply.Failure(NanoFailure.Busy))
        val cloud = FakeIntentProvider("gemini", Location.Cloud, ok(todoIntents, "gemini", Location.Cloud))
        val providers = AiProviders(intent = listOf(NanoProvider(client), cloud))
        val r = AiRouter(providers, AiPolicy({ true }, { true }), backoffMs = 1).route(
            app.cove.companion.ai.model.Capability.Intent, app.cove.companion.ai.model.Sensitivity.Everyday, providers.intent, 12,
        ) { it.parse(request) }
        assertEquals(2, client.calls)
        assertEquals("gemini", (r as AiResult.Ok).source.id)
    }

    @Test fun overlongTranscriptIsNotSentToNano() = runBlocking {
        val client = FakeNanoClient(NanoStatus.Available, NanoReply.Text(todoJson))
        val long = request.copy(transcript = "word ".repeat(3_000))
        assertTrue(NanoProvider(client).parse(long) is AiResult.Failed)
        assertEquals(0, client.calls)
    }

    @Test fun briefLineIsValidated() = runBlocking {
        val ok = NanoProvider(FakeNanoClient(NanoStatus.Available, NanoReply.Text("""{"text":"Good morning."}"""))).line(BriefRequest("intro", mapOf("a" to "b")))
        assertEquals("Good morning.", (ok as AiResult.Ok).value)
        val bad = NanoProvider(FakeNanoClient(NanoStatus.Available, NanoReply.Text("Good morning."))).line(BriefRequest("intro", emptyMap()))
        assertEquals(AiError.InvalidOutput, (bad as AiResult.Failed).error)
    }

    @Test fun summarizeCombinesThreeAnswers() = runBlocking {
        val client = FakeNanoClient(NanoStatus.Available, NanoReply.Text("A calm day."), NanoReply.Text("Work, #focus."), NanoReply.Text("Pretty calm"))
        val s = (NanoProvider(client).summarize("diary") as AiResult.Ok).value
        assertEquals("A calm day.", s.sentence)
        assertEquals(listOf("work", "focus"), s.tags)
        assertEquals("calm", s.mood)
        assertEquals(3, client.calls)
    }

    @Test fun summarizeFailsWhenTheFirstAnswerFails() = runBlocking {
        val client = FakeNanoClient(NanoStatus.Available, NanoReply.Failure(NanoFailure.QuotaExceeded))
        assertEquals(AiError.RateLimited, (NanoProvider(client).summarize("diary") as AiResult.Failed).error)
    }

    @Test fun captionUsesTheImagePrompt() = runBlocking {
        val client = FakeNanoClient(NanoStatus.Available, NanoReply.Text("A dog on a beach."))
        assertEquals("A dog on a beach.", (NanoProvider(client).caption(File("a.jpg")) as AiResult.Ok).value)
    }
}
