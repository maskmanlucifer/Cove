package app.cove.companion.feature.voice.speech

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/** On-device recognition through the ML Kit GenAI SpeechRecognizer (alpha API, Basic mode API 31+). */
class MlKitSpeechEngine : SpeechEngine {
    private val recognizer: SpeechRecognizer by lazy {
        SpeechRecognition.getClient(speechRecognizerOptions { locale = Locale.ENGLISH })
    }

    override val id = "mlkit"
    override val onDevice = true

    override suspend fun isAvailable(): Boolean = try {
        recognizer.checkStatus() == FeatureStatus.AVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        false
    }

    override fun listen(): Flow<SpeechEvent> = flow {
        var last = ""
        try {
            recognizer.startRecognition(speechRecognizerRequest { audioSource = AudioSource.fromMic() }).collect { r ->
                when (r) {
                    is SpeechRecognizerResponse.PartialTextResponse -> { last = r.text; emit(SpeechEvent.Partial(r.text)) }
                    is SpeechRecognizerResponse.FinalTextResponse -> { last = r.text; emit(SpeechEvent.Final(r.text)) }
                    is SpeechRecognizerResponse.ErrorResponse -> emit(SpeechEvent.Failure(SpeechFailure.Other))
                    is SpeechRecognizerResponse.CompletedResponse -> if (last.isNotBlank()) emit(SpeechEvent.Final(last))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(SpeechEvent.Failure(SpeechFailure.Other))
        }
    }

    override suspend fun stop() {
        try {
            recognizer.stopRecognition()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Unit
        }
    }
}
