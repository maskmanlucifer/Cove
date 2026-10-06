package app.cove.companion.ai

import app.cove.companion.ai.model.AdviceRequest
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.BriefInput
import app.cove.companion.ai.model.BriefLines
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.CloudCheck
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.model.TypedSession
import app.cove.companion.ai.model.VoiceIntent
import java.io.File

/**
 * The only AI type feature code depends on (`AppContainer.ai`). Capability-oriented: callers say what they need
 * and get a typed answer with provenance; which model, phone or cloud, produced it is the router's business.
 *
 * Privacy is built in. Journal-grade capabilities ([captionImage], [summarize], [embed]) only run on the phone
 * and are never sent to a cloud provider. See `docs/AI.md`.
 */
interface AiService {
    /** Understands a voice [transcript]: rules first, then Gemini Nano, then the cloud. */
    suspend fun parseIntent(transcript: String, context: IntentContext = IntentContext()): AiResult<ParsedIntents>

    /** Likely readings of a half-heard [text], from rules only (frame 19). */
    fun guessIntents(text: String): List<VoiceIntent>

    /** The optional generated intro and thought of the morning brief; [BriefInput] must hold non-journal facts only. */
    suspend fun composeBriefLines(facts: BriefInput): AiResult<BriefLines>

    /** Starts a microphone session on the best recognizer, or null when the phone has none (offer typing). */
    suspend fun openSpeech(): SpeechSession?

    /** A session fed by the keyboard instead of the microphone. */
    fun openTyped(): TypedSession

    /** One sentence describing the photo in [file]. On-device only. */
    suspend fun captionImage(file: File): AiResult<String>

    /** Sentence, tags and mood for a journal [text]. On-device only. */
    suspend fun summarize(text: String): AiResult<Summary>

    /** A vector for [text], for semantic search. On-device only. */
    suspend fun embed(text: String): AiResult<FloatArray>

    /**
     * Files up to [CategoryRequest.MAX_BATCH] expense [notes] under the user's [categories] (names). Everyday data:
     * only scrubbed note text and category names are sent. Manual use only: never call from a worker or automatically.
     * Suggestions refer to notes by position; nothing is changed by this call.
     */
    suspend fun suggestCategories(notes: List<String>, categories: List<String>): AiResult<List<CategorySuggestion>>

    /**
     * One-sentence second opinion on today's weight for a lift. Everyday data (lift name and recent sessions), manual use
     * only (the "Ask Cove" link); rules always answer first in the app, this never changes a weight by itself.
     */
    suspend fun adviseWorkout(request: AdviceRequest): AiResult<String>

    /** What each capability can use right now and why not, for settings screens. */
    suspend fun status(): AiStatus

    /** "Test connection" for the cloud language model with [apiKey] on [model]; never contains the key. */
    suspend fun testCloud(apiKey: String, model: String): CloudCheck
}
