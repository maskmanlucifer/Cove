package app.cove.companion.ai.provider

import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.AdviceRequest
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.model.Summary
import java.io.File

/**
 * One way of serving one capability. Implementations are thin adapters over a concrete API and never
 * decide privacy or ordering: `AiRouter` does. Failures are returned as [AiResult.Failed], not thrown.
 */
interface AiProvider {
    /** Stable lowercase id, for provenance and logs. */
    val id: String

    val location: Location

    /** Whether this provider can work now, ignoring the caller's [app.cove.companion.ai.model.Sensitivity]. */
    suspend fun availability(): Availability

    /** [id] and [location] together. */
    val ref: ProviderRef get() = ProviderRef(id, location)
}

/** Turns a transcript into intent drafts. */
interface IntentProvider : AiProvider {
    suspend fun parse(request: IntentRequest): AiResult<ParsedIntents>
}

/** Writes one short brief line from non-journal facts. */
interface BriefProvider : AiProvider {
    suspend fun line(request: BriefRequest): AiResult<String>
}

/** Opens microphone (or typed) sessions; [open] is only called when [availability] says so. */
interface SpeechProvider : AiProvider {
    fun open(): SpeechSession

    /**
     * Whether the engine reports input loudness. When true, a run with no level change is "no audio activity"
     * and hands over to the next engine; when false only the start-up and run-time rules apply.
     */
    val reportsLevels: Boolean get() = false

    /** Extra, content-free facts for the Voice check sheet (language pack, locale), one `label to value` per line. */
    suspend fun details(): List<Pair<String, String>> = emptyList()
}

/** Describes a photo in one sentence. */
interface CaptionProvider : AiProvider {
    suspend fun caption(image: File): AiResult<String>
}

/** Summarises, tags and reads the mood of a journal text. */
interface SummaryProvider : AiProvider {
    suspend fun summarize(text: String): AiResult<Summary>
}

/** Files a batch of expense notes under the user's categories. */
interface CategoryProvider : AiProvider {
    suspend fun suggest(request: CategoryRequest): AiResult<List<CategorySuggestion>>
}

/** One-sentence second opinion on a lift's weight (ordinary workout data). */
interface AdviceProvider : AiProvider {
    suspend fun advise(request: AdviceRequest): AiResult<String>
}

/** Turns text into a fixed-size vector for semantic search. */
interface EmbeddingProvider : AiProvider {
    suspend fun embed(text: String): AiResult<FloatArray>
}
