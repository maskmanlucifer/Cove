package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.provider.cloud.CloudProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudProviderTest {
    private val request = IntentRequest("x", IntentContext(), "2026-10-06T10:35", "UTC")
    private val todo = """{"intents":[{"type":"add_todo","items":[{"title":"Milk","category":null}]}]}"""

    private fun provider(gateway: FakeGateway) = CloudProvider("gemini", "API key") { gateway }

    @Test fun needsConfigUntilTheGatewayIsEnabled() = runBlocking {
        assertEquals(Availability.NeedsConfig("API key"), provider(FakeGateway(enabled = false)).availability())
        assertEquals(Availability.Available, provider(FakeGateway()).availability())
        assertEquals(Location.Cloud, provider(FakeGateway()).location)
    }

    @Test fun validAnswerCarriesCloudProvenance() = runBlocking {
        val r = provider(FakeGateway(intentReply = todo)).parse(request) as AiResult.Ok
        assertEquals("gemini/cloud", r.source.toString())
    }

    @Test fun journalNotesFromTheCloudAreDropped() = runBlocking {
        val journal = """{"intents":[{"type":"journal_note","text":"x"}]}"""
        assertEquals(AiError.InvalidOutput, (provider(FakeGateway(intentReply = journal)).parse(request) as AiResult.Failed).error)
    }

    @Test fun silentGatewayIsUnavailableNotACrash() = runBlocking {
        assertEquals(AiError.Unavailable("The cloud gave no answer"), (provider(FakeGateway()).parse(request) as AiResult.Failed).error)
        assertEquals(AiError.Unavailable("The cloud gave no answer"), (provider(FakeGateway()).line(BriefRequest("intro", emptyMap())) as AiResult.Failed).error)
        assertEquals("Hi", (provider(FakeGateway(lineReply = "Hi")).line(BriefRequest("intro", emptyMap())) as AiResult.Ok).value)
    }
}
