package app.cove.companion.ai.provider.ondevice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Fallback using the platform [SpeechRecognizer]: the on-device recognizer when the phone has one ([Location.Native]),
 * otherwise the default service, which may use the network ([Location.Cloud], so policy treats it as such).
 * Must be collected on the main thread.
 */
class AndroidSpeechProvider(private val context: Context) : SpeechProvider {
    private var active: SpeechRecognizer? = null

    override val id = "android-speech"
    private val onDevice: Boolean get() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    override val location: Location get() = if (onDevice) Location.Native else Location.Cloud

    override suspend fun availability(): Availability =
        if (onDevice || SpeechRecognizer.isRecognitionAvailable(context)) Availability.Available
        else Availability.Unavailable("This phone has no speech recognizer")

    override fun open(): SpeechSession = object : SpeechSession {
        override val source: ProviderRef = ref
        override val events: Flow<SpeechEvent> = listen()
        override suspend fun stop() { active?.stopListening() }
    }

    private fun listen(): Flow<SpeechEvent> = callbackFlow {
        val recognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        active = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                text(partialResults)?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle) {
                trySend(text(results)?.let { SpeechEvent.Final(it) } ?: SpeechEvent.Failure(SpeechFailure.NoMatch))
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failure(failure(error)))
                close()
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
        )
        awaitClose {
            recognizer.destroy()
            if (active === recognizer) active = null
        }
    }

    private fun text(b: Bundle) = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun failure(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NoMatch
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.Busy
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechFailure.Network
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PermissionDenied
        else -> SpeechFailure.Other
    }
}
