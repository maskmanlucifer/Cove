package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.model.SpeechSession
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.model.TodoDraft
import app.cove.companion.ai.provider.AiProvider
import app.cove.companion.ai.provider.BriefProvider
import app.cove.companion.ai.provider.CaptionProvider
import app.cove.companion.ai.provider.EmbeddingProvider
import app.cove.companion.ai.provider.IntentProvider
import app.cove.companion.ai.provider.SpeechProvider
import app.cove.companion.ai.provider.SummaryProvider
import app.cove.companion.ai.provider.cloud.CloudGateway
import app.cove.companion.ai.provider.ondevice.NanoClient
import app.cove.companion.ai.provider.ondevice.NanoFailure
import app.cove.companion.ai.provider.ondevice.NanoReply
import app.cove.companion.ai.provider.ondevice.NanoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

/** Shared behaviour of the fakes: scripted [script] answers (the last one repeats), a call counter and settable [state]. */
abstract class FakeProvider<T>(
    override val id: String,
    override val location: Location,
    var state: Availability = Availability.Available,
    private val script: List<AiResult<T>> = emptyList(),
) : AiProvider {
    var calls = 0
        private set
    var delayMs = 0L
    var throwOnCall: Throwable? = null

    override suspend fun availability() = state

    protected suspend fun answer(): AiResult<T> {
        val i = calls++
        throwOnCall?.let { throw it }
        if (delayMs > 0) delay(delayMs)
        return script.getOrElse(minOf(i, script.lastIndex)) { AiResult.Failed(AiError.Unavailable("no script"), listOf(ref)) }
    }
}

fun <T> ok(value: T, id: String = "fake", location: Location = Location.Native) = AiResult.Ok(value, ProviderRef(id, location))
fun fail(error: AiError, id: String = "fake", location: Location = Location.Native) = AiResult.Failed(error, listOf(ProviderRef(id, location)))

val todoIntents = ParsedIntents(listOf(VoiceIntent.AddTodos(listOf(TodoDraft("Walk the dog", null)))))

class FakeIntentProvider(id: String, location: Location, vararg script: AiResult<ParsedIntents>, state: Availability = Availability.Available) :
    FakeProvider<ParsedIntents>(id, location, state, script.toList()), IntentProvider {
    var last: IntentRequest? = null
    override suspend fun parse(request: IntentRequest): AiResult<ParsedIntents> { last = request; return answer() }
}

class FakeBriefProvider(id: String, location: Location, vararg script: AiResult<String>, state: Availability = Availability.Available) :
    FakeProvider<String>(id, location, state, script.toList()), BriefProvider {
    val requests = mutableListOf<BriefRequest>()
    override suspend fun line(request: BriefRequest): AiResult<String> { requests += request; return answer() }
}

class FakeCaptionProvider(id: String, location: Location, vararg script: AiResult<String>, state: Availability = Availability.Available) :
    FakeProvider<String>(id, location, state, script.toList()), CaptionProvider {
    override suspend fun caption(image: File): AiResult<String> = answer()
}

class FakeSummaryProvider(id: String, location: Location, vararg script: AiResult<Summary>, state: Availability = Availability.Available) :
    FakeProvider<Summary>(id, location, state, script.toList()), SummaryProvider {
    override suspend fun summarize(text: String): AiResult<Summary> = answer()
}

class FakeEmbeddingProvider(id: String, location: Location, vararg script: AiResult<FloatArray>, state: Availability = Availability.Available) :
    FakeProvider<FloatArray>(id, location, state, script.toList()), EmbeddingProvider {
    override suspend fun embed(text: String): AiResult<FloatArray> = answer()
}

class FakeSpeechProvider(id: String, location: Location, state: Availability = Availability.Available) :
    FakeProvider<Unit>(id, location, state), SpeechProvider {
    var opened = 0
    override fun open(): SpeechSession {
        opened++
        return object : SpeechSession {
            override val source = ref
            override val events = emptyFlow<SpeechEvent>()
            override suspend fun stop() = Unit
        }
    }
}

/** [NanoClient] with a scripted status and replies. */
class FakeNanoClient(var status: NanoStatus = NanoStatus.Available, vararg replies: NanoReply) : NanoClient {
    private val script = replies.toList()
    var calls = 0
        private set
    val prompts = mutableListOf<String>()

    override suspend fun status() = status
    override suspend fun generate(prompt: String): NanoReply = next(prompt)
    override suspend fun generate(prompt: String, image: File): NanoReply = next(prompt)

    private fun next(prompt: String): NanoReply {
        prompts += prompt
        return script.getOrElse(minOf(calls++, script.lastIndex)) { NanoReply.Failure(NanoFailure.Other) }
    }
}

/** [CloudGateway] with scripted answers that records what it was asked. */
class FakeGateway(
    override val enabled: Boolean = true,
    private val intentReply: String? = null,
    private val lineReply: String? = null,
) : CloudGateway {
    var intentCalls = 0
    var lineCalls = 0
    override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? { intentCalls++; return intentReply }
    override suspend fun briefLine(kind: String, facts: Map<String, String>): String? { lineCalls++; return lineReply }
}

/** An [AiService] with no providers, for tests of code that only needs one to exist. */
object NoopAi {
    private val providers = AiProviders()
    val service: AiService = DefaultAiService(
        AiRouter(providers, AiPolicy({ true }, { true })), providers,
        app.cove.companion.ai.provider.rules.TypedSpeechProvider(),
        app.cove.companion.ai.provider.rules.RuleParser(app.cove.companion.core.Clock.System),
        app.cove.companion.core.Clock.System,
    ) { _, _ -> app.cove.companion.ai.model.CloudCheck(false, "off") }
}
