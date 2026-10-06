package app.cove.companion.feature.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks short replies with Android TextToSpeech when [enabled] (the `spokenReplies` setting) allows it. */
class TtsSpeaker(context: Context, private val enabled: suspend () -> Boolean) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** Speaks [text] unless replies are switched off. Safe to call before the engine has started. */
    suspend fun speak(text: String) {
        if (!enabled()) return
        val engine = tts ?: TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.ENGLISH
                pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "cove") }
            }
            pending = null
        }.also { tts = it }
        if (ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cove") else pending = text
    }

    /** Cuts off speech, e.g. when listening starts again. */
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
