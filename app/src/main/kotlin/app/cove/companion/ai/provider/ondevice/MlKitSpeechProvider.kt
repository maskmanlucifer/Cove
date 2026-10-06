package app.cove.companion.ai.provider.ondevice

import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
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
class MlKitSpeechProvider : SpeechProvider {
    private val recognizer: SpeechRecognizer by lazy {
        SpeechRecognition.getClient(speechRecognizerOptions { locale = Locale.ENGLISH })
    }

    override val id = "mlkit-speech"
    override val location = Location.Native

    override suspend fun details(): List<Pair<String, String>> = listOf(
        "Mode" to "ML Kit GenAI speech (alpha), locale ${Locale.ENGLISH.toLanguageTag()}",
        "Feature status" to try { recognizer.checkStatus().toString() } catch (e: CancellationException) { throw e } catch (e: Throwable) { "unavailable (${e.javaClass.simpleName})" },
    )

    override suspend fun availability(): Availability = try {
        if (recognizer.checkStatus() == FeatureStatus.AVAILABLE) Availability.Available
        else Availability.Unavailable("The on-device speech model is not ready")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Availability.Unavailable("ML Kit speech recognition is unavailable on this device")
    }

    override fun open(): SpeechSession = object : SpeechSession {
        override val source: ProviderRef = ref
        override val events: Flow<SpeechEvent> = listen()
        override suspend fun stop() = stopRecognition()
    }

    private fun listen(): Flow<SpeechEvent> = flow {
        var last = ""
        emit(SpeechEvent.Ready(ref))
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

    private suspend fun stopRecognition() {
        try {
            recognizer.stopRecognition()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Unit
        }
    }
}
