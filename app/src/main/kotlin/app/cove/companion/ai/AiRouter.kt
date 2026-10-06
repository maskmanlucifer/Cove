package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.Sensitivity
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import app.cove.companion.ai.speech.SpeechChain
import app.cove.companion.ai.speech.SpeechDemotions
import app.cove.companion.ai.speech.SpeechTiming
import app.cove.companion.ai.provider.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks a provider for each request: walks the capability's providers in order, skips those [AiPolicy] refuses or
 * that report themselves unavailable, and falls through on failure. A [AiError.Busy] answer is retried once after
 * [backoffMs] (PLAN 6a), never looped; every other error moves straight to the next provider.
 */
class AiRouter(
    private val providers: AiProviders,
    private val policy: AiPolicy,
    private val backoffMs: Long = 400,
    private val callTimeoutMs: Long = 20_000,
    private val speechTiming: SpeechTiming = SpeechTiming(),
    private val speechLog: (String) -> Unit = {},
) {
    private val demotions = SpeechDemotions()

    /**
     * Runs [call] on the first provider of [capability] that policy and availability allow and that succeeds.
     *
     * @return the first [AiResult.Ok]; otherwise [AiResult.Failed] carrying the most informative error and the
     * providers that were actually called.
     */
    suspend fun <P : AiProvider, T> route(
        capability: Capability,
        sensitivity: Sensitivity,
        candidates: List<P>,
        payloadChars: Int = 0,
        call: suspend (P) -> AiResult<T>,
    ): AiResult<T> {
        val tried = mutableListOf<ProviderRef>()
        var skipped: AiError? = null
        var failed: AiError? = null
        for (p in candidates) {
            val blocked = policy.check(p.ref, capability, sensitivity, payloadChars)
            if (blocked != null) { skipped = skipped.orPrefer(blocked); continue }
            val unavailable = when (val a = p.availability()) {
                Availability.Available -> null
                is Availability.Unavailable -> AiError.Unavailable(a.reason)
                Availability.NeedsForeground -> AiError.NeedsForeground
                is Availability.NeedsConfig -> AiError.NeedsConfig(a.what)
            }
            if (unavailable != null) { skipped = skipped.orPrefer(unavailable); continue }
            tried += p.ref
            var result = attempt(p, call)
            if (result is AiResult.Failed && result.error == AiError.Busy) {
                delay(backoffMs)
                result = attempt(p, call)
            }
            when (result) {
                is AiResult.Ok -> return result
                is AiResult.Failed -> failed = result.error
            }
        }
        return AiResult.Failed(failed ?: skipped ?: AiError.Unavailable("No provider for $capability"), tried)
    }

    /** One call with a timeout; a provider that throws counts as unavailable, never as a crash. */
    private suspend fun <P : AiProvider, T> attempt(p: P, call: suspend (P) -> AiResult<T>): AiResult<T> = try {
        withTimeoutOrNull(callTimeoutMs) { call(p) } ?: AiResult.Failed(AiError.Timeout)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AiResult.Failed(AiError.Unavailable("${p.id} failed"))
    }

    /** Keeps the first skip reason that is not a privacy block, so "why nothing ran" stays useful. */
    private fun AiError?.orPrefer(next: AiError): AiError =
        if (this == null || this == AiError.PrivacyBlocked) next else this

    /** Microphone engines policy allows for [sensitivity], in priority order. A network recognizer is kept when offline: it may still run from an offline pack. */
    fun speechEngines(sensitivity: Sensitivity): List<SpeechProvider> = providers.speech.filter { p ->
        p.location != Location.Rules && policy.check(p.ref, Capability.Speech, sensitivity).let { it == null || it == AiError.Offline }
    }

    /**
     * A microphone session over every allowed engine with failover (see [SpeechChain]). Typed input is not a
     * microphone and is never part of it. With no engine at all the session ends at once with a failure the UI explains.
     */
    fun openSpeech(sensitivity: Sensitivity): SpeechSession =
        SpeechChain(speechEngines(sensitivity), speechTiming, demotions, log = speechLog)

    /** What each provider could do now; [AiStatus] marks the one that would serve a request. */
    suspend fun status(): AiStatus = AiStatus(
        Capability.entries.map { cap ->
            val sensitivity = if (cap == Capability.Caption || cap == Capability.Summary || cap == Capability.Embedding) Sensitivity.Journal else Sensitivity.Everyday
            var chosen = false
            CapabilityStatus(
                cap,
                providers.of(cap).map { p ->
                    val a = p.availability()
                    val usable = a == Availability.Available && policy.check(p.ref, cap, sensitivity) == null && !chosen
                    if (usable) chosen = true
                    ProviderStatus(p.ref, a, usable)
                },
            )
        },
    )
}
