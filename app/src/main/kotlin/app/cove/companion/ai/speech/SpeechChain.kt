package app.cove.companion.ai.speech

import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechAttempt
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The time rules of a listening run, in milliseconds. Defaults are what the app uses; tests shrink them.
 *
 * @property startMs longest an engine may take from opening to "ready for speech".
 * @property activityMs after ready, an engine that reports levels must show audio activity within this time.
 * @property noSpeechMs after ready, nobody has started speaking: stop the engine and call it silence.
 * @property minRunMs an engine without levels must have run this long after ready before its silence is believed.
 * @property maxMs hard cap for a whole run on one engine.
 * @property stopWaitMs how long to wait for a result after asking an engine to stop.
 */
data class SpeechTiming(
    val startMs: Long = 3_000,
    val activityMs: Long = 3_000,
    val noSpeechMs: Long = 7_000,
    val minRunMs: Long = 2_500,
    val maxMs: Long = 60_000,
    val stopWaitMs: Long = 2_000,
)

/** Remembers which engines failed recently so the next session tries them last instead of waiting out their grace period again. */
class SpeechDemotions(private val now: () -> Long = System::currentTimeMillis, private val ttlMs: Long = 10 * 60_000) {
    private val failedAt = HashMap<String, Long>()

    /** Marks [id] as having failed just now. */
    @Synchronized fun demote(id: String) { failedAt[id] = now() }

    /** Forgets [id] after it worked. */
    @Synchronized fun clear(id: String) { failedAt.remove(id) }

    /** Whether [id] failed within the last ten minutes. */
    @Synchronized fun isDemoted(id: String): Boolean = failedAt[id]?.let { now() - it < ttlMs } == true
}

/**
 * One listening session over an ordered list of engines with real failover: an engine that is unavailable, errors,
 * never becomes ready, or ends without any audio activity hands over to the next one, silently. Only when the
 * last engine has failed does [events] emit a [SpeechEvent.Failure], carrying the most informative reason.
 *
 * Silence is only reported when an engine really ran: it was ready and the microphone showed activity (or, for
 * engines without level reporting, it ran for [SpeechTiming.minRunMs]). A permission failure is never retried.
 * Words already heard win over any later error. Collect at most once at a time; a second collector gets
 * [SpeechFailure.Busy].
 *
 * @param engines candidates in priority order; typed input does not belong here.
 * @param log receives one content-free line per engine run and one for the session.
 */
class SpeechChain(
    engines: List<SpeechProvider>,
    private val timing: SpeechTiming = SpeechTiming(),
    private val demotions: SpeechDemotions? = null,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val log: (String) -> Unit = {},
) : SpeechSession {
    private val engines = engines.filterNot { demotions?.isDemoted(it.id) == true } + engines.filter { demotions?.isDemoted(it.id) == true }
    private val running = AtomicBoolean(false)

    @Volatile private var current: SpeechSession? = null

    @Volatile override var source: ProviderRef = this.engines.firstOrNull()?.ref ?: ProviderRef("none", Location.Rules)
        private set

    override suspend fun stop() { current?.stop() }

    override val events: Flow<SpeechEvent> = channelFlow {
        if (!running.compareAndSet(false, true)) {
            send(SpeechEvent.Failure(SpeechFailure.Busy))
            return@channelFlow
        }
        try {
            drive()
        } finally {
            current = null
            running.set(false)
        }
    }

    private sealed interface Step {
        data object Done : Step
        data class Surface(val reason: SpeechFailure, val code: Int) : Step
        data class Next(val reason: SpeechFailure, val code: Int) : Step
    }

    private suspend fun ProducerScope<SpeechEvent>.drive() {
        val started = clock()
        val attempts = mutableListOf<SpeechAttempt>()
        for (engine in engines) {
            val t0 = clock()
            val availability = try { engine.availability() } catch (e: CancellationException) { throw e } catch (e: Exception) { Availability.Unavailable("check failed") }
            if (availability != Availability.Available) {
                attempts += SpeechAttempt(engine.id, SpeechFailure.NoService, 0, 0)
                log("engine=${engine.id} unavailable")
                continue
            }
            when (val step = runEngine(engine)) {
                Step.Done -> {
                    demotions?.clear(engine.id)
                    log("engine=${engine.id} ok ms=${clock() - t0} session_ms=${clock() - started}")
                    return
                }
                is Step.Surface -> {
                    attempts += SpeechAttempt(engine.id, step.reason, step.code, clock() - t0)
                    if (step.reason != SpeechFailure.NoMatch) demotions?.demote(engine.id)
                    log("engine=${engine.id} ${step.reason} code=${step.code} ms=${clock() - t0} final=true")
                    send(SpeechEvent.Failure(step.reason, step.code, attempts))
                    return
                }
                is Step.Next -> {
                    attempts += SpeechAttempt(engine.id, step.reason, step.code, clock() - t0)
                    demotions?.demote(engine.id)
                    log("engine=${engine.id} ${step.reason} code=${step.code} ms=${clock() - t0} failover=true")
                }
            }
        }
        val best = attempts.minByOrNull { priority(it.reason) }
        log("all engines failed best=${best?.reason} session_ms=${clock() - started}")
        send(SpeechEvent.Failure(best?.reason ?: SpeechFailure.NoService, best?.code ?: 0, attempts))
    }

    /** Most useful to tell the user first: what they can fix beats what they cannot. */
    private fun priority(r: SpeechFailure) = when (r) {
        SpeechFailure.PermissionDenied -> 0
        SpeechFailure.Busy -> 1
        SpeechFailure.NoService -> 2
        SpeechFailure.Network -> 3
        SpeechFailure.NoMatch -> 4
        SpeechFailure.NoActivity -> 5
        SpeechFailure.Other -> 6
    }

    /** Runs one engine until it produces a transcript, a surfaced failure, or a reason to hand over. */
    private suspend fun ProducerScope<SpeechEvent>.runEngine(engine: SpeechProvider): Step {
        val session = try { engine.open() } catch (e: CancellationException) { throw e } catch (e: Exception) { return Step.Next(SpeechFailure.Other, 0) }
        current = session
        source = engine.ref
        val inbox = Channel<SpeechEvent>(Channel.UNLIMITED)
        val reader = launch {
            try {
                session.events.collect { inbox.send(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                inbox.trySend(SpeechEvent.Failure(SpeechFailure.Other))
            }
            inbox.close()
        }
        val t0 = clock()
        var readyAt = -1L
        var activity = false
        var spoke = false
        var partial = ""
        var stopAt = -1L

        fun genuine() = readyAt >= 0 && (activity || (!engine.reportsLevels && clock() - readyAt >= timing.minRunMs))

        suspend fun conclude(reason: SpeechFailure, code: Int): Step = when {
            reason == SpeechFailure.PermissionDenied -> Step.Surface(reason, code)
            partial.isNotBlank() -> { send(SpeechEvent.Final(partial)); Step.Done }
            reason == SpeechFailure.NoMatch -> if (genuine()) Step.Surface(SpeechFailure.NoMatch, code) else Step.Next(SpeechFailure.NoActivity, code)
            else -> Step.Next(reason, code)
        }

        try {
            while (true) {
                val now = clock()
                val deadline = when {
                    stopAt >= 0 -> stopAt + timing.stopWaitMs
                    readyAt < 0 -> t0 + timing.startMs
                    spoke -> t0 + timing.maxMs
                    engine.reportsLevels && !activity -> readyAt + timing.activityMs
                    else -> readyAt + timing.noSpeechMs
                }
                val got = withTimeoutOrNull((deadline - now).coerceAtLeast(0)) { inbox.receiveCatching() }
                if (got == null) {
                    when {
                        stopAt >= 0 -> return conclude(SpeechFailure.NoMatch, 0)
                        readyAt < 0 -> return Step.Next(SpeechFailure.NoActivity, 0)
                        spoke -> { runCatching { session.stop() }; stopAt = clock() }
                        engine.reportsLevels && !activity -> return Step.Next(SpeechFailure.NoActivity, 0)
                        else -> { runCatching { session.stop() }; stopAt = clock() }
                    }
                    continue
                }
                if (got.isClosed) return conclude(SpeechFailure.NoMatch, 0)
                when (val e = got.getOrThrow()) {
                    is SpeechEvent.Ready -> { readyAt = clock(); send(SpeechEvent.Ready(engine.ref)) }
                    is SpeechEvent.Level -> { activity = true; send(e) }
                    SpeechEvent.Began -> { activity = true; spoke = true; send(e) }
                    is SpeechEvent.Partial -> { activity = true; spoke = true; partial = e.text; send(e) }
                    is SpeechEvent.Final -> return if (e.text.isBlank()) conclude(SpeechFailure.NoMatch, 0) else { send(e); Step.Done }
                    is SpeechEvent.Failure -> return conclude(e.reason, e.code)
                }
            }
        } finally {
            reader.cancelAndJoin()
            inbox.cancel()
            if (current === session) current = null
        }
    }
}
