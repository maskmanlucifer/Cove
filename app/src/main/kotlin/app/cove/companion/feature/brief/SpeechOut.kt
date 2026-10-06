package app.cove.companion.feature.brief

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Why a phone cannot read the brief aloud. */
enum class TtsProblem {
    /** No text-to-speech engine is installed or it refused to start. */
    NoEngine,

    /** The engine has no English voice data. */
    LanguageMissing,

    /** The engine did not answer in time. */
    Timeout,

    /** An utterance failed while speaking. */
    Failed,
}

/** Callbacks of a [SpeechOut]; [id] is the utterance id given to `speak`. */
interface SpeechListener {
    fun onStart(id: String)
    fun onRange(id: String, start: Int)
    fun onDone(id: String)

    /** Speech cannot happen; queued text is dropped. */
    fun onProblem(problem: TtsProblem) {}
}

/** Queue-based text-to-speech, abstracted so the player's logic can be tested without Android. */
interface SpeechOut {
    var listener: SpeechListener?

    /** Adds [text] to the queue; [rate] is the speaking speed (1 = normal). */
    fun speak(id: String, text: String, rate: Float)

    /** Drops everything queued and cuts off the current utterance. */
    fun stop()

    fun shutdown()
}

/**
 * [SpeechOut] over Android TextToSpeech; starts the engine lazily and holds early requests until it is ready.
 * A missing engine, missing English data, a stalled start ([INIT_TIMEOUT_MS]) or a failed utterance is reported through
 * [SpeechListener.onProblem] instead of leaving the player waiting.
 */
class AndroidSpeechOut(context: Context) : SpeechOut {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var started = false
    private val noSound = Runnable { if (!started) fail(TtsProblem.Timeout) }
    private val waiting = mutableListOf<Triple<String, String, Float>>()
    override var listener: SpeechListener? = null

    override fun speak(id: String, text: String, rate: Float) {
        if (failed) return
        val engine = tts ?: create()
        if (ready) engine.enqueue(id, text, rate) else waiting += Triple(id, text, rate)
    }

    private fun TextToSpeech.enqueue(id: String, text: String, rate: Float) {
        setSpeechRate(rate)
        if (speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) return fail(TtsProblem.Failed)
        // An engine that accepts text but never starts speaking (no voice data, stalled service) must not look like playback.
        if (!started) {
            main.removeCallbacks(noSound)
            main.postDelayed(noSound, FIRST_SOUND_TIMEOUT_MS)
        }
    }

    private fun fail(problem: TtsProblem) {
        if (failed) return
        failed = true
        main.removeCallbacksAndMessages(null)
        waiting.clear()
        listener?.onProblem(problem)
    }

    private fun create(): TextToSpeech {
        val engine = TextToSpeech(appContext) { status ->
            main.post {
                main.removeCallbacksAndMessages(null)
                if (failed) return@post
                if (status != TextToSpeech.SUCCESS) return@post fail(TtsProblem.NoEngine)
                val language = tts?.setLanguage(Locale.ENGLISH)
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    return@post fail(TtsProblem.LanguageMissing)
                }
                ready = true
                waiting.toList().forEach { (id, text, rate) -> tts?.enqueue(id, text, rate) }
                waiting.clear()
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                started = true
                main.removeCallbacks(noSound)
                listener?.onStart(utteranceId)
            }
            override fun onDone(utteranceId: String) { listener?.onDone(utteranceId) }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { main.post { fail(TtsProblem.Failed) } }
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) { listener?.onRange(utteranceId, start) }
        })
        tts = engine
        main.postDelayed({ if (!ready) fail(TtsProblem.Timeout) }, INIT_TIMEOUT_MS)
        return engine
    }

    override fun stop() {
        waiting.clear()
        main.removeCallbacks(noSound)
        tts?.stop()
    }

    override fun shutdown() {
        main.removeCallbacksAndMessages(null)
        waiting.clear()
        tts?.shutdown()
        tts = null
        ready = false
        failed = false
        started = false
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 6000L
        const val FIRST_SOUND_TIMEOUT_MS = 8000L
    }
}
