package app.cove.companion.feature.voice

import app.cove.companion.AppContainer
import app.cove.companion.BuildConfig

/**
 * Debug-only hooks to drive the Voice screen into a given frame:
 * `--es voiceState listening|result|partial|saved|micoff --es transcript "..."`.
 */
object VoiceDebug {
    /** A requested frame. */
    data class Request(val state: String, val transcript: String, val seconds: Int)

    @Volatile
    private var pending: Request? = null

    /** Whether the intent asked for the saved frame, which runs a command instead of opening Voice. */
    var savedRequested = false
        private set

    /** Remembers the request from intent extras; call only in debug builds. */
    fun set(state: String?, transcript: String?, seconds: Int) {
        if (!BuildConfig.DEBUG || state == null) return
        val text = transcript ?: DEFAULT
        if (state == "saved") savedRequested = true else pending = Request(state, text, seconds)
        savedTranscript = text
    }

    private var savedTranscript = DEFAULT

    val hasPending get() = pending != null

    /** Takes the pending request, so it applies only once. */
    fun consume(): Request? = pending.also { pending = null }

    /** Runs the transcript as a saved command so Today shows the "Saved" chip and "New" tags (frame 20). */
    suspend fun runSaved(c: AppContainer) {
        if (!savedRequested) return
        savedRequested = false
        val intents = c.ai.parseIntent(savedTranscript).valueOrNull()?.intents ?: return
        val r = c.voice.executor.execute(savedTranscript, intents)
        c.voice.newTodos.add(r.createdTodos)
        c.voice.feedback.show(r.summary, r.commandId)
    }

    private const val DEFAULT = "Add milk, batteries and fix the bathroom tap"
}
