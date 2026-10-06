package app.cove.companion.feature.brief

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Callbacks of a [SpeechOut]; [id] is the utterance id given to `speak`. */
interface SpeechListener {
    fun onStart(id: String)
    fun onRange(id: String, start: Int)
    fun onDone(id: String)
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

/** [SpeechOut] over Android TextToSpeech; starts the engine lazily and holds early requests until it is ready. */
class AndroidSpeechOut(context: Context) : SpeechOut {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private val waiting = mutableListOf<Triple<String, String, Float>>()
    override var listener: SpeechListener? = null

    override fun speak(id: String, text: String, rate: Float) {
        val engine = tts ?: create()
        if (ready) engine.enqueue(id, text, rate) else waiting += Triple(id, text, rate)
    }

    private fun TextToSpeech.enqueue(id: String, text: String, rate: Float) {
        setSpeechRate(rate)
        speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    private fun create(): TextToSpeech {
        val engine = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.ENGLISH
                waiting.forEach { (id, text, rate) -> tts?.enqueue(id, text, rate) }
            }
            waiting.clear()
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { listener?.onStart(utteranceId) }
            override fun onDone(utteranceId: String) { listener?.onDone(utteranceId) }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { listener?.onDone(utteranceId) }
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) { listener?.onRange(utteranceId, start) }
        })
        tts = engine
        return engine
    }

    override fun stop() {
        waiting.clear()
        tts?.stop()
    }

    override fun shutdown() {
        waiting.clear()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
