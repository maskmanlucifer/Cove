package app.cove.companion.feature.voice.speech

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The "engine" behind Type instead: [submit] emits a final transcript through the same path as speech. */
class TypedSpeechEngine : SpeechEngine {
    private val typed = Channel<String>(Channel.CONFLATED)

    override val id = "typed"
    override val onDevice = true
    override suspend fun isAvailable() = true

    override fun listen(): Flow<SpeechEvent> = flow {
        emit(SpeechEvent.Final(typed.receive()))
    }

    override suspend fun stop() = Unit

    /** Delivers [text] as if it had been spoken. */
    fun submit(text: String) {
        typed.trySend(text)
    }
}
