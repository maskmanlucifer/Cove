package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.BriefInput
import app.cove.companion.ai.model.BriefLines
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.CloudCheck
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.Sensitivity
import app.cove.companion.ai.model.SpeechEngineInfo
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.speech.SpeechDebug
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.model.TypedSession
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.TypedSpeechProvider
import app.cove.companion.core.Clock
import app.cove.companion.core.toLocalDateTime
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [AiService] on top of an [AiRouter]. Builds typed requests and hands every call to the router with the
 * [Sensitivity] its capability is allowed to have.
 *
 * @param typed the typed-input provider, also listed in `providers.speech` for status.
 * @param rules used only for [guessIntents].
 * @param cloudCheck runs "Test connection" for the cloud provider.
 */
class DefaultAiService(
    private val router: AiRouter,
    private val providers: AiProviders,
    private val typed: TypedSpeechProvider,
    private val rules: RuleParser,
    private val clock: Clock,
    private val cloudCheck: suspend (apiKey: String, model: String) -> CloudCheck,
) : AiService {
    override suspend fun parseIntent(transcript: String, context: IntentContext): AiResult<ParsedIntents> {
        val text = transcript.trim()
        if (text.isEmpty()) return AiResult.Failed(AiError.Unavailable("Nothing was heard"))
        val now = clock.now().toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substringBeforeLast(':').take(16)
        val request = IntentRequest(text, context, now, ZoneId.systemDefault().id)
        return router.route(Capability.Intent, Sensitivity.Everyday, providers.intent, text.length) { it.parse(request) }
    }

    override fun guessIntents(text: String): List<VoiceIntent> = rules.guesses(text)

    override suspend fun composeBriefLines(facts: BriefInput): AiResult<BriefLines> {
        val safe = facts.facts.filterKeys { !it.contains("journal", ignoreCase = true) }
        val intro = line(BriefRequest("intro", if (facts.name != null) safe + ("name" to facts.name) else safe))
        val thought = line(BriefRequest("thought", safe))
        val source = (intro as? AiResult.Ok)?.source ?: (thought as? AiResult.Ok)?.source
            ?: return (intro as AiResult.Failed)
        return AiResult.Ok(BriefLines(intro.valueOrNull(), thought.valueOrNull()), source)
    }

    private suspend fun line(request: BriefRequest) =
        router.route(Capability.Brief, Sensitivity.Everyday, providers.brief) { it.line(request) }

    override suspend fun openSpeech(): SpeechSession = SpeechDebug.session() ?: router.openSpeech(Sensitivity.Everyday)

    override suspend fun speechEngines(): List<SpeechEngineInfo> = providers.speech.filter { it.location != Location.Rules }.map { p ->
        SpeechEngineInfo(p.ref, p.availability(), try { p.details() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() })
    }

    override fun openSpeechEngine(id: String): SpeechSession? = providers.speech.firstOrNull { it.id == id && it.location != Location.Rules }?.open()

    override fun openTyped(): TypedSession = typed.openTyped()

    override suspend fun captionImage(file: File): AiResult<String> =
        router.route(Capability.Caption, Sensitivity.Journal, providers.caption) { it.caption(file) }

    override suspend fun summarize(text: String): AiResult<Summary> =
        router.route(Capability.Summary, Sensitivity.Journal, providers.summary) { it.summarize(text) }

    override suspend fun embed(text: String): AiResult<FloatArray> =
        router.route(Capability.Embedding, Sensitivity.Journal, providers.embedding) { it.embed(text) }

    override suspend fun suggestCategories(notes: List<String>, categories: List<String>): AiResult<List<CategorySuggestion>> {
        if (notes.isEmpty() || categories.isEmpty()) return AiResult.Failed(AiError.Unavailable("Nothing to check"))
        if (notes.size > CategoryRequest.MAX_BATCH) return AiResult.Failed(AiError.Unavailable("Too many at once"))
        val request = CategoryRequest(notes, categories)
        return router.route(Capability.Category, Sensitivity.Everyday, providers.category) { it.suggest(request) }
    }

    override suspend fun status(): AiStatus = router.status()

    override suspend fun testCloud(apiKey: String, model: String): CloudCheck = cloudCheck(apiKey, model)
}
