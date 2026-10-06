package app.cove.companion.ai.model

import kotlinx.coroutines.flow.Flow

/** Something a speech session reports while listening. */
sealed interface SpeechEvent {
    /** Words heard so far (replaces the previous partial). */
    data class Partial(val text: String) : SpeechEvent

    /** The finished transcript; the flow ends after it. */
    data class Final(val text: String) : SpeechEvent

    /** Input loudness 0..1, drives the orb. */
    data class Level(val value: Float) : SpeechEvent

    data class Failure(val reason: SpeechFailure) : SpeechEvent
}

enum class SpeechFailure { NoMatch, Busy, Network, PermissionDenied, Other }

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
