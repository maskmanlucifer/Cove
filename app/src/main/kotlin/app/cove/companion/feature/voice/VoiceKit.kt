package app.cove.companion.feature.voice

import android.content.Context
import app.cove.companion.AppContainer
import app.cove.companion.feature.voice.exec.IntentExecutor
import app.cove.companion.feature.voice.exec.RepoVoiceStore
import kotlinx.coroutines.flow.first

/** The voice assistant's services, created lazily by [AppContainer.voice] so they outlive the screen. */
class VoiceKit(context: Context, container: AppContainer) {
    val executor = IntentExecutor(RepoVoiceStore(container), container.clock)
    val speaker = TtsSpeaker(context) { container.settings.settings.first().spokenReplies }
    val feedback = VoiceFeedback()
    val newTodos = NewTracker(context)
}
