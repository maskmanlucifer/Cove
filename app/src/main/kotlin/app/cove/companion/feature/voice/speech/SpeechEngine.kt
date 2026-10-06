package app.cove.companion.feature.voice.speech

import kotlinx.coroutines.flow.Flow

/** Something a [SpeechEngine] reports while listening. */
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

/** A way of turning speech (or typing) into a transcript. */
interface SpeechEngine {
    val id: String

    /** True when words stay on the phone, so the screen can say "on this phone". */
    val onDevice: Boolean

    /** Whether this engine can run right now (service present, model downloaded). */
    suspend fun isAvailable(): Boolean

    /** Starts listening; collecting begins capture and cancelling the collector stops it. */
    fun listen(): Flow<SpeechEvent>

    /** Asks for the final result of what was heard so far; [listen] then emits [SpeechEvent.Final]. */
    suspend fun stop()
}

/** Picks the first available engine in priority order (ML Kit, Android recognizer), or null. */
class SpeechEngines(private val engines: List<SpeechEngine>) {
    suspend fun pick(): SpeechEngine? = engines.firstOrNull { it.isAvailable() }
}
