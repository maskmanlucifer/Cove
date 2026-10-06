package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefInput
import app.cove.companion.ai.model.CloudCheck
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.provider.rules.RuleIntentProvider
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.TypedSpeechProvider
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAiServiceTest {
    private val clock = Clock { LocalDateTime.of(2026, 10, 6, 10, 35).toEpochMillis() }
    private val rules = RuleParser(clock)
    private val unknown = "could you maybe sort the dog situation"
    private var foreground = true

    private fun service(providers: AiProviders, check: suspend (String, String) -> CloudCheck = { _, _ -> CloudCheck(true, "ok") }): AiService {
        val router = AiRouter(providers, AiPolicy({ foreground }, { true }), backoffMs = 1)
        return DefaultAiService(router, providers, TypedSpeechProvider(), rules, clock, check)
    }

    private val nano get() = FakeIntentProvider("nano", Location.Native, ok(todoIntents, "nano"))
    private val cloud get() = FakeIntentProvider("gemini", Location.Cloud, ok(todoIntents, "gemini", Location.Cloud))

    @Test fun rulesWinAndSkipModels() = runBlocking {
        val n = nano; val c = cloud
        val r = service(AiProviders(intent = listOf(RuleIntentProvider(rules), n, c))).parseIntent("remind me to call mum at 6") as AiResult.Ok
        assertEquals("rules", r.source.id)
        assertEquals(0, n.calls + c.calls)
    }

    @Test fun nanoThenCloudThenFailure() = runBlocking {
        val ruleProvider = RuleIntentProvider(rules)
        val viaNano = service(AiProviders(intent = listOf(ruleProvider, nano, cloud))).parseIntent(unknown) as AiResult.Ok
        assertEquals("nano", viaNano.source.id)
        val badNano = FakeIntentProvider("nano", Location.Native, fail(AiError.InvalidOutput))
        val viaCloud = service(AiProviders(intent = listOf(ruleProvider, badNano, cloud))).parseIntent(unknown) as AiResult.Ok
        assertEquals("gemini", viaCloud.source.id)
        foreground = false
        val c = cloud
        assertTrue(service(AiProviders(intent = listOf(ruleProvider, nano, FakeIntentProvider("gemini", Location.Cloud, fail(AiError.Offline))))).parseIntent(unknown) is AiResult.Failed)
        assertEquals("gemini", (service(AiProviders(intent = listOf(ruleProvider, nano, c))).parseIntent(unknown) as AiResult.Ok).source.id)
    }

    @Test fun longTranscriptsNeverReachTheCloud() = runBlocking {
        val c = cloud
        val long = "so " + "many words ".repeat(30)
        service(AiProviders(intent = listOf(RuleIntentProvider(rules), c))).parseIntent(long)
        assertEquals(0, c.calls)
    }

    @Test fun emptyTranscriptFailsWithoutCallingAnyone() = runBlocking {
        val n = nano
        assertTrue(service(AiProviders(intent = listOf(n))).parseIntent("   ") is AiResult.Failed)
        assertEquals(0, n.calls)
    }

    @Test fun guessesComeFromRules() {
        assertEquals(2, service(AiProviders()).guessIntents("…mind me… the… at four…").size)
    }

    @Test fun briefDropsJournalFactsAndAddsNameToIntroOnly() = runBlocking {
        val brief = FakeBriefProvider("gemini", Location.Cloud, ok("A line.", "gemini", Location.Cloud))
        val r = service(AiProviders(brief = listOf(brief))).composeBriefLines(
            BriefInput(mapOf("weather" to "sun", "journal_text" to "secret"), name = "Asha"),
        ) as AiResult.Ok
        assertEquals("A line.", r.value.intro)
        assertEquals("A line.", r.value.thought)
        assertEquals(listOf("intro", "thought"), brief.requests.map { it.kind })
        assertTrue(brief.requests.none { "journal_text" in it.facts })
        assertEquals("Asha", brief.requests[0].facts["name"])
        assertEquals(null, brief.requests[1].facts["name"])
    }

    @Test fun briefPrefersNanoInTheForegroundAndCloudOtherwise() = runBlocking {
        val nanoBrief = FakeBriefProvider("nano", Location.Native, ok("Nano line", "nano"))
        val cloudBrief = FakeBriefProvider("gemini", Location.Cloud, ok("Cloud line", "gemini", Location.Cloud))
        val svc = service(AiProviders(brief = listOf(nanoBrief, cloudBrief)))
        assertEquals("nano", (svc.composeBriefLines(BriefInput(mapOf("a" to "b"))) as AiResult.Ok).source.id)
        foreground = false
        assertEquals("gemini", (svc.composeBriefLines(BriefInput(mapOf("a" to "b"))) as AiResult.Ok).source.id)
    }

    @Test fun briefFailsWhenNothingAnswers() = runBlocking {
        val brief = FakeBriefProvider("gemini", Location.Cloud, state = Availability.NeedsConfig("API key"))
        assertEquals(AiError.NeedsConfig("API key"), (service(AiProviders(brief = listOf(brief))).composeBriefLines(BriefInput(emptyMap())) as AiResult.Failed).error)
    }

    @Test fun journalGradeCapabilitiesNeverCallACloudProviderEvenIfOneIsListed() = runBlocking {
        val summary = Summary("s", listOf("t"), "calm")
        val cloudSummary = FakeSummaryProvider("gemini", Location.Cloud, ok(summary, "gemini", Location.Cloud))
        val cloudCaption = FakeCaptionProvider("gemini", Location.Cloud, ok("c", "gemini", Location.Cloud))
        val cloudEmbed = FakeEmbeddingProvider("gemini", Location.Cloud, ok(floatArrayOf(1f), "gemini", Location.Cloud))
        val brokenNano = FakeSummaryProvider("nano", Location.Native, fail(AiError.InvalidOutput, "nano"))
        val svc = service(
            AiProviders(
                summary = listOf(brokenNano, cloudSummary),
                caption = listOf(cloudCaption),
                embedding = listOf(cloudEmbed),
            ),
        )
        assertTrue(svc.summarize("my day") is AiResult.Failed)
        assertEquals(AiError.PrivacyBlocked, (svc.captionImage(File("x.jpg")) as AiResult.Failed).error)
        assertEquals(AiError.PrivacyBlocked, (svc.embed("my day") as AiResult.Failed).error)
        assertEquals(0, cloudSummary.calls + cloudCaption.calls + cloudEmbed.calls)
    }

    @Test fun journalGradeCapabilitiesUseNativeProvidersWithProvenance() = runBlocking {
        val summary = Summary("s", listOf("t"), "calm")
        val svc = service(
            AiProviders(
                summary = listOf(FakeSummaryProvider("nano", Location.Native, ok(summary, "nano"))),
                caption = listOf(FakeCaptionProvider("nano", Location.Native, ok("A dog.", "nano"))),
                embedding = listOf(FakeEmbeddingProvider("embedder", Location.Native, ok(floatArrayOf(1f, 2f), "embedder"))),
            ),
        )
        assertEquals(summary, (svc.summarize("my day") as AiResult.Ok).value)
        assertEquals("nano", (svc.captionImage(File("x.jpg")) as AiResult.Ok).source.id)
        assertEquals(2, (svc.embed("my day") as AiResult.Ok).value.size)
        foreground = false
        assertEquals(AiError.NeedsForeground, (svc.summarize("my day") as AiResult.Failed).error)
    }

    @Test fun testCloudDelegatesAndStatusIsReported() = runBlocking {
        val svc = service(AiProviders(intent = listOf(nano, cloud)), check = { key, model -> CloudCheck(key == "k", model) })
        assertEquals(CloudCheck(true, "m"), svc.testCloud("k", "m"))
        assertEquals("On-device AI: available · Cloud AI: key set", svc.status().summary)
    }

    @Test fun typedSessionDeliversSubmittedText() = runBlocking {
        val session = service(AiProviders()).openTyped()
        session.submit("add milk")
        val event = session.events.first()
        assertEquals("add milk", (event as app.cove.companion.ai.model.SpeechEvent.Final).text)
    }
}
