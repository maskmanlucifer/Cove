package app.cove.companion.ai.speech

import app.cove.companion.BuildConfig
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Debug builds only: `--es voiceFail <mode>` replaces the microphone engines by scripted fakes so every guidance
 * screen can be shown on an emulator without a microphone. Modes: `permission`, `busy`, `noservice`, `network`,
 * `silence` (engine heard the mic, nobody spoke), `noactivity` (engine never captured audio), `failover` (first engine
 * errors at once, second one hears "add milk and eggs"), `listen` (listens for ever with moving levels).
 */
object SpeechDebug {
    @Volatile var mode: String? = null

    /** A fake chain for the current [mode], or null in release builds and when no mode is set. */
    fun session(): SpeechSession? {
        val m = mode ?: return null
        if (!BuildConfig.DEBUG) return null
        val fast = SpeechTiming(startMs = 600, activityMs = 600, noSpeechMs = 1_500, minRunMs = 400)
        val engines = when (m) {
            "permission" -> listOf(Fake("fake-a", Location.Native) { fail(SpeechFailure.PermissionDenied, 9) })
            "busy" -> listOf(Fake("fake-a", Location.Native) { fail(SpeechFailure.Busy, 8) }, Fake("fake-b", Location.Cloud) { fail(SpeechFailure.Busy, 8) })
            "noservice" -> listOf(Fake("fake-a", Location.Native) { fail(SpeechFailure.NoService, 13) }, Fake("fake-b", Location.Cloud) { fail(SpeechFailure.NoService, 13) })
            "network" -> listOf(Fake("fake-a", Location.Native) { fail(SpeechFailure.NoService, 13) }, Fake("fake-b", Location.Cloud) { fail(SpeechFailure.Network, 2) })
            "silence" -> listOf(Fake("fake-a", Location.Native, levels = true) { ready(); levels(); delay(2_000); fail(SpeechFailure.NoMatch, 7) })
            "noactivity" -> listOf(Fake("fake-a", Location.Native, levels = true) { ready(); delay(400); fail(SpeechFailure.NoMatch, 7) }, Fake("fake-b", Location.Cloud, levels = true) { ready(); delay(400); fail(SpeechFailure.NoMatch, 7) })
            "failover" -> listOf(Fake("fake-a", Location.Native) { fail(SpeechFailure.Other, 5) }, Fake("fake-b", Location.Cloud, levels = true) {
                ready(); levels(); emit(SpeechEvent.Began)
                delay(700); emit(SpeechEvent.Partial("add milk")); delay(900); emit(SpeechEvent.Partial("add milk and eggs")); delay(900)
                emit(SpeechEvent.Final("Add milk and eggs"))
            })
            "listen" -> listOf(Fake("fake-a", Location.Native, levels = true) { ready(); while (true) { levels(); delay(250) } })
            else -> return null
        }
        return SpeechChain(engines, if (m == "listen") SpeechTiming(maxMs = 600_000, noSpeechMs = 600_000) else fast)
    }

    private class Fake(
        override val id: String,
        override val location: Location,
        val levels: Boolean = false,
        private val script: suspend kotlinx.coroutines.flow.FlowCollector<SpeechEvent>.() -> Unit,
    ) : SpeechProvider {
        override val reportsLevels get() = levels
        override suspend fun availability(): Availability = Availability.Available
        override fun open() = object : SpeechSession {
            override val source: ProviderRef = ref
            override val events: Flow<SpeechEvent> = flow { script() }
            override suspend fun stop() = Unit
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<SpeechEvent>.ready() = emit(SpeechEvent.Ready(ProviderRef("fake", Location.Native)))
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SpeechEvent>.fail(r: SpeechFailure, code: Int) = emit(SpeechEvent.Failure(r, code))
    private var tick = 0
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SpeechEvent>.levels() = emit(SpeechEvent.Level(0.2f + 0.6f * ((tick++ % 7) / 6f)))
}
