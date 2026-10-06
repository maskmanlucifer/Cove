package app.cove.companion.ai.provider.ondevice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.provider.SpeechProvider
import app.cove.companion.ai.speech.SpeechErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/**
 * The platform [SpeechRecognizer] as two engines so the chain can fail over between them:
 * [Mode.OnDevice] (API 31+ `createOnDeviceSpeechRecognizer`, needs an installed language pack, never uses the network)
 * and [Mode.System] (the default recognition service, usually the Google app, which may use the network).
 *
 * All recognizer calls run on the main thread. Language is the device locale, then en-IN, then en-US: a recognizer
 * that refuses a language (error 12 or 13) is retried with the next one before the engine gives up.
 */
class AndroidSpeechProvider(private val context: Context, private val mode: Mode) : SpeechProvider {
    /** Which recognizer to create. */
    enum class Mode(val engineId: String) { OnDevice("android-ondevice"), System("android-system") }

    override val id = mode.engineId
    override val location: Location get() = if (mode == Mode.OnDevice) Location.Native else Location.Cloud
    override val reportsLevels = true

    private val main = Handler(Looper.getMainLooper())

    override suspend fun availability(): Availability = when (mode) {
        Mode.OnDevice ->
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) Availability.Available
            else Availability.Unavailable("No on-device recognizer (offline speech pack) on this phone")
        Mode.System ->
            if (SpeechRecognizer.isRecognitionAvailable(context)) Availability.Available
            else Availability.Unavailable("No speech recognition service found (install or enable the Google app)")
    }

    override fun open(): SpeechSession = object : SpeechSession {
        override val source: ProviderRef = ref
        override val events: Flow<SpeechEvent> = listen()
        override suspend fun stop() { main.post { active?.runCatching { stopListening() } } }
    }

    @Volatile private var active: SpeechRecognizer? = null

    private fun create(): SpeechRecognizer =
        if (mode == Mode.OnDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)

    private fun listen(): Flow<SpeechEvent> = callbackFlow {
        val tags = languageTags()
        var tagIndex = 0
        var lastLevel = -1f
        var recognizer: SpeechRecognizer? = null

        fun start() {
            try {
                recognizer?.startListening(recognizerIntent(tags[tagIndex]))
            } catch (e: SecurityException) {
                trySend(SpeechEvent.Failure(SpeechFailure.PermissionDenied, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
                close()
            } catch (e: RuntimeException) {
                trySend(SpeechEvent.Failure(SpeechFailure.Other, SpeechRecognizer.ERROR_CLIENT))
                close()
            }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Ready(ref)) }
            override fun onBeginningOfSpeech() { trySend(SpeechEvent.Began) }

            override fun onRmsChanged(rmsdB: Float) {
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (lastLevel < 0f || abs(level - lastLevel) >= 0.02f) {
                    lastLevel = level
                    trySend(SpeechEvent.Level(level))
                }
            }

            override fun onPartialResults(partialResults: Bundle) {
                text(partialResults)?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle) {
                trySend(text(results)?.let { SpeechEvent.Final(it) } ?: SpeechEvent.Failure(SpeechFailure.NoMatch, SpeechRecognizer.ERROR_NO_MATCH))
                close()
            }

            override fun onError(error: Int) {
                val languageRefused = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                if (languageRefused && tagIndex + 1 < tags.size) {
                    tagIndex++
                    main.post { start() }
                    return
                }
                trySend(SpeechEvent.Failure(failure(error), error))
                close()
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        main.post {
            try {
                recognizer = create().also { it.setRecognitionListener(listener) }
                active = recognizer
                start()
            } catch (e: SecurityException) {
                trySend(SpeechEvent.Failure(SpeechFailure.PermissionDenied, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
                close()
            } catch (e: RuntimeException) {
                trySend(SpeechEvent.Failure(SpeechFailure.NoService, SpeechRecognizer.ERROR_CLIENT))
                close()
            }
        }
        awaitClose {
            main.post {
                val r = recognizer
                r?.runCatching { cancel() }
                r?.runCatching { destroy() }
                if (active === r) active = null
            }
        }
    }

    private fun recognizerIntent(tag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MS)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLY_COMPLETE_SILENCE_MS)

    private fun text(b: Bundle) = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun details(): List<Pair<String, String>> {
        val rows = mutableListOf("Language tried" to languageTags().joinToString(", "))
        if (mode == Mode.System) {
            val service = runCatching { Settings.Secure.getString(context.contentResolver, "voice_recognition_service") }.getOrNull()
            rows += "Default recognizer" to (service?.let { ComponentName.unflattenFromString(it)?.packageName ?: it } ?: "not reported")
        }
        rows += "Language pack" to languagePackStatus()
        return rows
    }

    /** Asks the recognizer which languages work offline (API 33+); never blocks longer than a few seconds. */
    private suspend fun languagePackStatus(): String {
        if (Build.VERSION.SDK_INT < 33) return "unknown (needs Android 13)"
        if (mode == Mode.OnDevice && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return "no on-device recognizer"
        return withContext(Dispatchers.Main) {
            var recognizer: SpeechRecognizer? = null
            try {
                recognizer = create()
                val support = withTimeoutOrNull(3_000) {
                    suspendCancellableCoroutine<Any> { cont ->
                        recognizer.checkRecognitionSupport(
                            recognizerIntent(languageTags().first()),
                            { it.run() },
                            object : RecognitionSupportCallback {
                                override fun onSupportResult(recognitionSupport: RecognitionSupport) { if (cont.isActive) cont.resumeWith(Result.success(recognitionSupport)) }
                                override fun onError(error: Int) { if (cont.isActive) cont.resumeWith(Result.success(error)) }
                            },
                        )
                    }
                }
                when (support) {
                    is RecognitionSupport -> "installed ${support.installedOnDeviceLanguages.ifEmpty { listOf("none") }.joinToString()}; " +
                        "downloadable ${support.supportedOnDeviceLanguages.size}; online ${support.onlineLanguages.size}"
                    is Int -> "could not check (${SpeechErrors.describe(support)}, code $support)"
                    else -> "could not check (no answer)"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "could not check (${e.javaClass.simpleName})"
            } finally {
                recognizer?.runCatching { destroy() }
            }
        }
    }

    private companion object {
        /** Pauses of this length end a run; long enough for a thought, and the app keeps listening across runs anyway. */
        const val COMPLETE_SILENCE_MS = 4_000L
        const val POSSIBLY_COMPLETE_SILENCE_MS = 3_000L
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13

        fun languageTags(): List<String> = listOf(Locale.getDefault().toLanguageTag(), "en-IN", "en-US").distinct()

        fun failure(code: Int) = SpeechErrors.failure(code)
    }
}
