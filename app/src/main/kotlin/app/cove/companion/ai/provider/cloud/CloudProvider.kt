package app.cove.companion.ai.provider.cloud

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.prompt.CategoryPrompt
import app.cove.companion.ai.provider.BriefProvider
import app.cove.companion.ai.provider.CategoryProvider
import app.cove.companion.ai.provider.IntentProvider
import app.cove.companion.ai.schema.CategorySchema
import app.cove.companion.ai.schema.IntentSchema

/**
 * Cloud adapter over a [CloudGateway]: Gemini direct ([GEMINI_ID]) or the legacy Edge Function ([EDGE_ID]).
 * [gateway] is re-read on every call so a credential change takes effect without rebuilding the router.
 * Journal notes in a cloud answer are dropped: journal text is only ever produced on the phone.
 */
class CloudProvider(
    override val id: String,
    private val needs: String,
    private val gateway: () -> CloudGateway,
) : IntentProvider, BriefProvider, CategoryProvider {
    override val location = Location.Cloud

    override suspend fun availability(): Availability =
        if (gateway().enabled) Availability.Available else Availability.NeedsConfig(needs)

    override suspend fun parse(request: IntentRequest): AiResult<ParsedIntents> {
        val raw = gateway().parseIntent(request.transcript, request.now, request.zone, request.context.todoCategories)
            ?: return failed(AiError.Unavailable("The cloud gave no answer"))
        val intents = IntentSchema.parse(raw)?.filterNot { it is VoiceIntent.JournalNote }?.takeIf { it.isNotEmpty() }
            ?: return failed(AiError.InvalidOutput)
        return AiResult.Ok(ParsedIntents(intents), ref)
    }

    override suspend fun line(request: BriefRequest): AiResult<String> =
        gateway().briefLine(request.kind, request.facts)?.let { AiResult.Ok(it, ref) }
            ?: failed(AiError.Unavailable("The cloud gave no answer"))

    override suspend fun suggest(request: CategoryRequest): AiResult<List<CategorySuggestion>> {
        val raw = gateway().suggestCategories(CategoryPrompt.SYSTEM, CategoryPrompt.user(request))
            ?: return failed(AiError.Unavailable("The cloud gave no answer"))
        val list = CategorySchema.parse(raw, request.notes.size, request.categories) ?: return failed(AiError.InvalidOutput)
        return AiResult.Ok(list, ref)
    }

    private fun failed(error: AiError) = AiResult.Failed(error, listOf(ref))

    companion object {
        const val GEMINI_ID = "gemini"
        const val EDGE_ID = "supabase-edge"
    }
}
