package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.TypedSession
import app.cove.companion.ai.provider.SpeechProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** "Speech" from the keyboard: always available, always local, last in the speech order. */
class TypedSpeechProvider : SpeechProvider {
    override val id = "typed"
    override val location = Location.Rules

    override suspend fun availability() = Availability.Available

    override fun open(): TypedSession = openTyped()

    /** A new typed session; [TypedSession.submit] delivers its transcript. */
    fun openTyped(): TypedSession = object : TypedSession {
        private val typed = Channel<String>(Channel.CONFLATED)
        override val source: ProviderRef = ref
        override val events: Flow<SpeechEvent> = flow { emit(SpeechEvent.Final(typed.receive())) }
        override suspend fun stop() = Unit
        override fun submit(text: String) {
            typed.trySend(text)
        }
    }
}
