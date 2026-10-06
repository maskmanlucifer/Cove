package app.cove.companion.feature.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Fallback using the platform [SpeechRecognizer]: the on-device recognizer when the phone has one,
 * otherwise the default service (which may use the network). Must be collected on the main thread.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var active: SpeechRecognizer? = null

    override val id = "android"
    override val onDevice: Boolean get() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun isAvailable() = onDevice || SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
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

    override suspend fun stop() {
        active?.stopListening()
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
