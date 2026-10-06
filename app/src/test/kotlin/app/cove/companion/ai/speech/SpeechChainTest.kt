package app.cove.companion.ai.speech

import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scripted engine: [script] plays when the session is collected; [stopped] completes when the chain asks it to stop. */
private class Engine(
    override val id: String,
    override val reportsLevels: Boolean = true,
    private val state: Availability = Availability.Available,
    private val script: suspend FlowCollector<SpeechEvent>.(stopped: CompletableDeferred<Unit>) -> Unit,
) : SpeechProvider {
    override val location = Location.Native
    var opened = 0
    val stopped = CompletableDeferred<Unit>()
    override suspend fun availability() = state
    override fun open(): SpeechSession {
        opened++
        return object : SpeechSession {
            override val source = ref
            override val events: Flow<SpeechEvent> = flow { script(stopped) }
            override suspend fun stop() { stopped.complete(Unit) }
        }
    }
}

private suspend fun FlowCollector<SpeechEvent>.ready(id: String = "x") = emit(SpeechEvent.Ready(ProviderRef(id, Location.Native)))
private suspend fun FlowCollector<SpeechEvent>.fail(r: SpeechFailure, code: Int = 0) = emit(SpeechEvent.Failure(r, code))

class SpeechChainTest {
    private val fast = SpeechTiming(startMs = 150, activityMs = 150, noSpeechMs = 300, minRunMs = 100, maxMs = 5_000, stopWaitMs = 200)

    private fun run(vararg engines: Engine, timing: SpeechTiming = fast, demotions: SpeechDemotions? = null, log: (String) -> Unit = {}): List<SpeechEvent> =
        runBlocking { SpeechChain(engines.toList(), timing, demotions, log = log).events.toList() }

    private fun List<SpeechEvent>.terminal() = last()

    @Test fun instantErrorHandsOverSilentlyAndSecondEngineSucceeds() {
        val a = Engine("a") { fail(SpeechFailure.Other, 5) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Level(0.4f)); emit(SpeechEvent.Final("add milk")) }
        val events = run(a, b)
        assertEquals(SpeechEvent.Final("add milk"), events.terminal())
        assertTrue(events.none { it is SpeechEvent.Failure })
        assertEquals(1, a.opened)
    }

    @Test fun engineWithoutActivityAfterReadyFailsOver() {
        val a = Engine("a") { ready(); delay(10_000) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("hello")) }
        assertEquals(SpeechEvent.Final("hello"), run(a, b).terminal())
    }

    @Test fun engineThatNeverGetsReadyFailsOver() {
        val a = Engine("a") { delay(10_000) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("hello")) }
        assertEquals(SpeechEvent.Final("hello"), run(a, b).terminal())
    }

    @Test fun busyEngineFailsOverAndAllBusyReportsBusyWithAttempts() {
        val a = Engine("a") { fail(SpeechFailure.Busy, 8) }
        val b = Engine("b") { fail(SpeechFailure.Busy, 8) }
        val f = run(a, b).terminal() as SpeechEvent.Failure
        assertEquals(SpeechFailure.Busy, f.reason)
        assertEquals(8, f.code)
        assertEquals(listOf("a", "b"), f.attempts.map { it.engine })
    }

    @Test fun permissionDeniedIsNotRetriedOnOtherEngines() {
        val a = Engine("a") { fail(SpeechFailure.PermissionDenied, 9) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("never")) }
        val f = run(a, b).terminal() as SpeechEvent.Failure
        assertEquals(SpeechFailure.PermissionDenied, f.reason)
        assertEquals(0, b.opened)
    }

    @Test fun silenceIsReportedOnlyWhenTheMicrophoneShowedActivity() {
        val a = Engine("a") { ready(); emit(SpeechEvent.Level(0.2f)); delay(20); fail(SpeechFailure.NoMatch, 7) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("never")) }
        val f = run(a, b).terminal() as SpeechEvent.Failure
        assertEquals(SpeechFailure.NoMatch, f.reason)
        assertEquals(0, b.opened)
    }

    @Test fun noMatchWithoutAnyAudioActivityIsNotSilenceAndFailsOver() {
        val a = Engine("a") { ready(); fail(SpeechFailure.NoMatch, 7) }
        val b = Engine("b") { ready(); fail(SpeechFailure.NoMatch, 7) }
        val f = run(a, b).terminal() as SpeechEvent.Failure
        assertEquals(SpeechFailure.NoActivity, f.reason)
        assertEquals(1, b.opened)
    }

    @Test fun engineWithoutLevelsNeedsMinimumRunTimeBeforeSilenceIsBelieved() {
        val quick = Engine("quick", reportsLevels = false) { ready(); fail(SpeechFailure.NoMatch) }
        val next = Engine("next", reportsLevels = false) { ready(); delay(150); fail(SpeechFailure.NoMatch) }
        val f = run(quick, next).terminal() as SpeechEvent.Failure
        assertEquals(1, quick.opened)
        assertEquals(1, next.opened)
        assertEquals(SpeechFailure.NoMatch, f.reason)
        assertEquals(listOf(SpeechFailure.NoActivity, SpeechFailure.NoMatch), f.attempts.map { it.reason })
    }

    @Test fun nobodySpeakingStopsTheEngineAndReportsSilence() {
        val a = Engine("a") { stopped ->
            ready(); emit(SpeechEvent.Level(0.3f))
            stopped.await(); fail(SpeechFailure.NoMatch, 7)
        }
        val f = run(a).terminal() as SpeechEvent.Failure
        assertTrue(a.stopped.isCompleted)
        assertEquals(SpeechFailure.NoMatch, f.reason)
    }

    @Test fun wordsAlreadyHeardWinOverALaterError() {
        val a = Engine("a") { ready(); emit(SpeechEvent.Began); emit(SpeechEvent.Partial("remind me")); fail(SpeechFailure.Network, 2) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("never")) }
        assertEquals(SpeechEvent.Final("remind me"), run(a, b).terminal())
        assertEquals(0, b.opened)
    }

    @Test fun unavailableEnginesAreSkippedAndNoEngineAtAllExplainsItself() {
        val gone = Engine("gone", state = Availability.Unavailable("no")) { ready() }
        val f = run(gone).terminal() as SpeechEvent.Failure
        assertEquals(SpeechFailure.NoService, f.reason)
        assertEquals(0, gone.opened)
        assertEquals(SpeechFailure.NoService, (run().terminal() as SpeechEvent.Failure).reason)
    }

    @Test fun mostFixableReasonWinsWhenAllEnginesFail() {
        val a = Engine("a") { fail(SpeechFailure.Other, 5) }
        val b = Engine("b") { fail(SpeechFailure.NoService, 13) }
        assertEquals(SpeechFailure.NoService, (run(a, b).terminal() as SpeechEvent.Failure).reason)
    }

    @Test fun secondCollectorWhileRunningGetsBusy() = runBlocking {
        val a = Engine("a") { ready(); emit(SpeechEvent.Level(0.5f)); delay(300); emit(SpeechEvent.Final("ok")) }
        val chain = SpeechChain(listOf(a), fast)
        val first = async { chain.events.toList() }
        delay(100)
        val second = chain.events.toList()
        assertEquals(SpeechFailure.Busy, (second.single() as SpeechEvent.Failure).reason)
        assertEquals(SpeechEvent.Final("ok"), first.await().last())
    }

    @Test fun recentlyFailedEnginesAreTriedLast() {
        val demotions = SpeechDemotions()
        val a = Engine("a") { fail(SpeechFailure.Other, 5) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("one")) }
        run(a, b, demotions = demotions)
        assertTrue(demotions.isDemoted("a"))
        val a2 = Engine("a") { ready(); emit(SpeechEvent.Final("from a")) }
        val b2 = Engine("b") { ready(); emit(SpeechEvent.Final("from b")) }
        assertEquals(SpeechEvent.Final("from b"), run(a2, b2, demotions = demotions).terminal())
        assertEquals(0, a2.opened)
    }

    @Test fun logLinesNameEngineAndCodeButNeverText() {
        val lines = mutableListOf<String>()
        val a = Engine("a") { fail(SpeechFailure.Other, 5) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("secret words")) }
        run(a, b, log = { lines += it })
        assertTrue(lines.any { it.contains("engine=a") && it.contains("code=5") })
        assertTrue(lines.none { it.contains("secret") })
    }

    @Test fun sourceFollowsTheEngineThatListens() = runBlocking {
        val a = Engine("a") { fail(SpeechFailure.Other, 5) }
        val b = Engine("b") { ready(); emit(SpeechEvent.Final("x")) }
        val chain = SpeechChain(listOf(a, b), fast)
        chain.events.toList()
        assertEquals("b", chain.source.id)
        assertNull(null)
    }
}
