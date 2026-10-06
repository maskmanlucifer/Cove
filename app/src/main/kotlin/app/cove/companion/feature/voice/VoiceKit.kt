package app.cove.companion.feature.voice

import android.content.Context
import app.cove.companion.AppContainer
import app.cove.companion.BuildConfig
import app.cove.companion.data.ai.KtorAiGateway
import app.cove.companion.data.ai.MlKitOnDeviceLlm
import app.cove.companion.feature.voice.exec.IntentExecutor
import app.cove.companion.feature.voice.exec.RepoVoiceStore
import app.cove.companion.feature.voice.intent.IntentParser
import app.cove.companion.feature.voice.speech.AndroidSpeechEngine
import app.cove.companion.feature.voice.speech.MlKitSpeechEngine
import app.cove.companion.feature.voice.speech.SpeechEngines
import kotlinx.coroutines.flow.first

/** The voice assistant's services, created lazily by [AppContainer.voice] so they outlive the screen. */
class VoiceKit(context: Context, container: AppContainer) {
    val executor = IntentExecutor(RepoVoiceStore(container), container.clock)
    val parser = IntentParser(
        container.clock,
        MlKitOnDeviceLlm(),
        KtorAiGateway(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
    )
    val engines = SpeechEngines(listOf(MlKitSpeechEngine(), AndroidSpeechEngine(context)))
    val speaker = TtsSpeaker(context) { container.settings.settings.first().spokenReplies }
    val feedback = VoiceFeedback()
    val newTodos = NewTracker(context)
}
