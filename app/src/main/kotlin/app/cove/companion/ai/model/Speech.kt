package app.cove.companion.ai.model

import kotlinx.coroutines.flow.Flow

/** Something a speech session reports while listening. */
sealed interface SpeechEvent {
    /** An engine finished starting and is capturing audio: the user can speak now. [source] is who listens. */
    data class Ready(val source: ProviderRef) : SpeechEvent

    /** The engine detected the start of speech. */
    data object Began : SpeechEvent

    /** Words heard so far (replaces the previous partial). */
    data class Partial(val text: String) : SpeechEvent

    /** The finished transcript; the flow ends after it. */
    data class Final(val text: String) : SpeechEvent

    /** Input loudness 0..1, drives the orb. Only sent when the level changed. */
    data class Level(val value: Float) : SpeechEvent

    /**
     * The session ended without a transcript; the flow ends after it.
     *
     * @property code the engine's native error code (Android `SpeechRecognizer.ERROR_*`), 0 when it has none.
     * @property attempts every engine tried, in order, for diagnostics (content-free).
     */
    data class Failure(val reason: SpeechFailure, val code: Int = 0, val attempts: List<SpeechAttempt> = emptyList()) : SpeechEvent
}

/**
 * Why a recognition run produced nothing.
 *
 * [NoMatch] is true silence: the engine ran, the microphone delivered audio, nobody spoke. [NoActivity] means the
 * engine never showed signs of capturing audio, so it says nothing about the user.
 */
enum class SpeechFailure { NoMatch, NoActivity, Busy, Network, PermissionDenied, NoService, Other }

/** What one engine did in one run: for logs and the Voice check sheet. Never contains speech. */
data class SpeechAttempt(val engine: String, val reason: SpeechFailure, val code: Int, val elapsedMs: Long) {
    /** "android-system: NoActivity (code 7) after 3010 ms". */
    override fun toString() = "$engine: ${reason.name}${if (code != 0) " (code $code)" else ""} after $elapsedMs ms"
}

/** One listening run: collect [events] to capture, cancel the collector to abort, [stop] to finish early. */
interface SpeechSession {
    /** Who is transcribing, so the screen can say "on this phone" when it is local. */
    val source: ProviderRef

    /** Starts capture on collection; ends after [SpeechEvent.Final] or [SpeechEvent.Failure]. */
    val events: Flow<SpeechEvent>

    /** Asks for the final result of what was heard so far; [events] then emits [SpeechEvent.Final]. */
    suspend fun stop()
}

/** A [SpeechSession] fed by the keyboard: [submit] delivers text through the same path as speech. */
interface TypedSession : SpeechSession {
    /** Delivers [text] as if it had been spoken. */
    fun submit(text: String)
}

/** One microphone engine as the Voice check sheet shows it. [details] are `label to value`, content-free. */
data class SpeechEngineInfo(val ref: ProviderRef, val availability: Availability, val details: List<Pair<String, String>>)
