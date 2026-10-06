package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.Sensitivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterTest {
    private var foreground = true
    private var online = true
    private val policy = AiPolicy({ foreground }, { online })
    private val request = IntentRequest("x", IntentContext(), "2026-10-06T10:35", "UTC")

    private fun router(vararg providers: FakeIntentProvider) =
        AiRouter(AiProviders(intent = providers.toList()), policy, backoffMs = 1, callTimeoutMs = 200)

    private fun AiRouter.run(providers: List<FakeIntentProvider>, sensitivity: Sensitivity = Sensitivity.Everyday, chars: Int = 1) = runBlocking {
        route(Capability.Intent, sensitivity, providers, chars) { it.parse(request) }
    }

    private fun intent(id: String, location: Location, vararg script: AiResult<ParsedIntents>, state: Availability = Availability.Available) =
        FakeIntentProvider(id, location, *script, state = state)

    @Test fun firstAvailableProviderWinsAndLaterOnesAreNotCalled() {
        val rules = intent("rules", Location.Rules, ok(todoIntents, "rules", Location.Rules))
        val nano = intent("nano", Location.Native, ok(todoIntents, "nano"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        val result = router(rules, nano, cloud).run(listOf(rules, nano, cloud))
        assertEquals(ProviderRef("rules", Location.Rules), (result as AiResult.Ok).source)
        assertEquals(0, nano.calls + cloud.calls)
    }

    @Test fun fallsThroughInOrderOnFailure() {
        val rules = intent("rules", Location.Rules, fail(AiError.Unavailable("no rule"), "rules", Location.Rules))
        val nano = intent("nano", Location.Native, fail(AiError.InvalidOutput, "nano"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        val result = router(rules, nano, cloud).run(listOf(rules, nano, cloud)) as AiResult.Ok
        assertEquals("cloud", result.source.id)
        assertEquals(1, rules.calls); assertEquals(1, nano.calls); assertEquals(1, cloud.calls)
    }

    @Test fun skipsUnavailableAndUnconfiguredWithoutCalling() {
        val nano = intent("nano", Location.Native, ok(todoIntents), state = Availability.Unavailable("no model"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents), state = Availability.NeedsConfig("API key"))
        val result = router(nano, cloud).run(listOf(nano, cloud)) as AiResult.Failed
        assertEquals(0, nano.calls + cloud.calls)
        assertEquals(AiError.Unavailable("no model"), result.error)
        assertTrue(result.tried.isEmpty())
    }

    @Test fun configGatesCloud() {
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents), state = Availability.NeedsConfig("API key"))
        assertEquals(AiError.NeedsConfig("API key"), (router(cloud).run(listOf(cloud)) as AiResult.Failed).error)
        assertEquals(0, cloud.calls)
    }

    @Test fun nativeOnlyRunsInTheForeground() {
        val nano = intent("nano", Location.Native, ok(todoIntents, "nano"))
        foreground = false
        assertEquals(AiError.NeedsForeground, (router(nano).run(listOf(nano)) as AiResult.Failed).error)
        assertEquals(0, nano.calls)
        foreground = true
        assertTrue(router(nano).run(listOf(nano)) is AiResult.Ok)
    }

    @Test fun foregroundDoesNotGateRulesOrCloud() {
        foreground = false
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        assertTrue(router(cloud).run(listOf(cloud)) is AiResult.Ok)
    }

    @Test fun cloudNeedsANetwork() {
        online = false
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        assertEquals(AiError.Offline, (router(cloud).run(listOf(cloud)) as AiResult.Failed).error)
        assertEquals(0, cloud.calls)
    }

    @Test fun cloudNeverSeesJournalSensitivityEvenWhenNativeFails() {
        val nano = intent("nano", Location.Native, fail(AiError.InvalidOutput, "nano"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        val r = router(nano, cloud).run(listOf(nano, cloud), Sensitivity.Journal) as AiResult.Failed
        assertEquals(0, cloud.calls)
        assertEquals(AiError.InvalidOutput, r.error)
        assertEquals(listOf(ProviderRef("nano", Location.Native)), r.tried)
    }

    @Test fun journalWithNothingNativeReportsPrivacyBlockedAndCallsNothing() {
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        val r = router(cloud).run(listOf(cloud), Sensitivity.Journal) as AiResult.Failed
        assertEquals(AiError.PrivacyBlocked, r.error)
        assertEquals(0, cloud.calls)
    }

    @Test fun journalPrivacyHoldsForEveryCapabilityAndLocation() {
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        Capability.entries.forEach { cap ->
            val r = runBlocking { router(cloud).route(cap, Sensitivity.Journal, listOf(cloud), 0) { it.parse(request) } }
            assertTrue("$cap", r is AiResult.Failed)
        }
        assertEquals(0, cloud.calls)
    }

    @Test fun longTextStaysOffTheCloud() {
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        assertEquals(AiError.PrivacyBlocked, (router(cloud).run(listOf(cloud), chars = 161) as AiResult.Failed).error)
        assertTrue(router(cloud).run(listOf(cloud), chars = 160) is AiResult.Ok)
    }

    @Test fun busyIsRetriedOnceThenNextProviderIsUsed() {
        val nano = intent("nano", Location.Native, fail(AiError.Busy, "nano"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        val r = router(nano, cloud).run(listOf(nano, cloud)) as AiResult.Ok
        assertEquals(2, nano.calls)
        assertEquals("cloud", r.source.id)
    }

    @Test fun busyThatClearsOnRetryStaysOnTheProvider() {
        val nano = intent("nano", Location.Native, fail(AiError.Busy, "nano"), ok(todoIntents, "nano"))
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        assertEquals("nano", (router(nano, cloud).run(listOf(nano, cloud)) as AiResult.Ok).source.id)
        assertEquals(0, cloud.calls)
    }

    @Test fun quotaIsNotRetried() {
        val nano = intent("nano", Location.Native, fail(AiError.RateLimited, "nano"))
        val r = router(nano).run(listOf(nano)) as AiResult.Failed
        assertEquals(1, nano.calls)
        assertEquals(AiError.RateLimited, r.error)
    }

    @Test fun slowProviderTimesOutAndFallsThrough() {
        val slow = intent("slow", Location.Native, ok(todoIntents, "slow")).also { it.delayMs = 5_000 }
        val cloud = intent("cloud", Location.Cloud, ok(todoIntents, "cloud", Location.Cloud))
        assertEquals("cloud", (router(slow, cloud).run(listOf(slow, cloud)) as AiResult.Ok).source.id)
        assertEquals(AiError.Timeout, (router(slow).run(listOf(slow)) as AiResult.Failed).error)
    }

    @Test fun aThrowingProviderCountsAsUnavailableNotACrash() {
        val bad = intent("bad", Location.Native, ok(todoIntents, "bad")).also { it.throwOnCall = IllegalStateException("boom") }
        val good = intent("good", Location.Rules, ok(todoIntents, "good", Location.Rules))
        assertEquals("good", (router(bad, good).run(listOf(bad, good)) as AiResult.Ok).source.id)
    }

    @Test fun provenanceNamesTheProviderThatAnswered() {
        val nano = intent("nano", Location.Native, ok(todoIntents, "nano"))
        val r = router(nano).run(listOf(nano)) as AiResult.Ok
        assertEquals("nano/native", r.source.toString())
    }

    @Test fun speechSkipsTypedAndPicksFirstAvailableRecognizer() = runBlocking {
        val mlkit = FakeSpeechProvider("mlkit", Location.Native, Availability.Unavailable("no model"))
        val android = FakeSpeechProvider("android", Location.Native)
        val typed = FakeSpeechProvider("typed", Location.Rules)
        val r = AiRouter(AiProviders(speech = listOf(mlkit, android, typed)), policy)
        assertEquals("android", r.openSpeech(Sensitivity.Everyday)!!.source.id)
        assertEquals(0, typed.opened)
        android.state = Availability.Unavailable("none")
        assertEquals(null, r.openSpeech(Sensitivity.Everyday))
    }

    @Test fun networkRecognizerIsRefusedForJournalAndWhenOffline() = runBlocking {
        val net = FakeSpeechProvider("android", Location.Cloud)
        val r = AiRouter(AiProviders(speech = listOf(net)), policy)
        assertEquals(null, r.openSpeech(Sensitivity.Journal))
        online = false
        assertEquals(null, r.openSpeech(Sensitivity.Everyday))
        online = true
        assertFalse(r.openSpeech(Sensitivity.Everyday) == null)
    }

    @Test fun statusListsEveryProviderAndMarksTheActiveOne() = runBlocking {
        val rules = FakeIntentProvider("rules", Location.Rules)
        val nano = FakeIntentProvider("nano", Location.Native, state = Availability.Unavailable("Gemini Nano unavailable on this device"))
        val cloud = FakeIntentProvider("gemini", Location.Cloud, state = Availability.NeedsConfig("API key"))
        val status = AiRouter(AiProviders(intent = listOf(rules, nano, cloud)), policy).status()
        val intent = status.of(Capability.Intent)
        assertEquals(listOf("rules", "nano", "gemini"), intent.providers.map { it.ref.id })
        assertEquals("rules", intent.active!!.id)
        assertEquals("Gemini Nano unavailable on this device", status.onDevice)
        assertEquals("no API key", status.cloud)
        assertEquals("On-device AI: Gemini Nano unavailable on this device · Cloud AI: no API key", status.summary)
    }

    @Test fun statusSaysAvailableAndKeySet() = runBlocking {
        val nano = FakeIntentProvider("nano", Location.Native)
        val cloud = FakeIntentProvider("gemini", Location.Cloud)
        val status = AiRouter(AiProviders(intent = listOf(nano, cloud)), policy).status()
        assertEquals("On-device AI: available · Cloud AI: key set", status.summary)
    }

    @Test fun statusNeverActivatesCloudForJournalCapabilities() = runBlocking {
        val cloudSummary = FakeSummaryProvider("gemini", Location.Cloud)
        val status = AiRouter(AiProviders(summary = listOf(cloudSummary)), policy).status()
        assertEquals(null, status.of(Capability.Summary).active)
    }
}
