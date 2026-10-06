package app.cove.companion.ai.provider.ondevice

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.prompt.BriefPrompt
import app.cove.companion.ai.prompt.IntentPrompt
import app.cove.companion.ai.prompt.JournalPrompt
import app.cove.companion.ai.provider.BriefProvider
import app.cove.companion.ai.provider.CaptionProvider
import app.cove.companion.ai.provider.IntentProvider
import app.cove.companion.ai.provider.SummaryProvider
import app.cove.companion.ai.schema.BriefSchema
import app.cove.companion.ai.schema.IntentSchema
import app.cove.companion.ai.schema.JournalSchema
import java.io.File

/**
 * Gemini Nano as intent parser, brief writer, summariser and photo captioner. One id, one availability: the
 * model either is on this phone or is not. Busy and quota answers are reported as such so the router can
 * back off once and then move on.
 */
class NanoProvider(private val client: NanoClient) : IntentProvider, BriefProvider, SummaryProvider, CaptionProvider {
    override val id = ID
    override val location = Location.Native

    override suspend fun availability(): Availability = when (client.status()) {
        NanoStatus.Available -> Availability.Available
        NanoStatus.Downloadable -> Availability.Unavailable("Gemini Nano is not downloaded yet")
        NanoStatus.Downloading -> Availability.Unavailable("Gemini Nano is still downloading")
        NanoStatus.Unavailable -> Availability.Unavailable("Gemini Nano unavailable on this device")
    }

    override suspend fun parse(request: IntentRequest): AiResult<ParsedIntents> {
        val prompt = IntentPrompt.nano(request) ?: return failed(AiError.Unavailable("Too long for the on-device model"))
        return ask(client.generate(prompt)) { raw ->
            IntentSchema.parse(raw)?.takeIf { it.isNotEmpty() }?.let(::ParsedIntents)
        }
    }

    override suspend fun line(request: BriefRequest): AiResult<String> {
        val prompt = BriefPrompt.nano(request) ?: return failed(AiError.Unavailable("Too long for the on-device model"))
        return ask(client.generate(prompt), BriefSchema::validateLine)
    }

    override suspend fun summarize(text: String): AiResult<Summary> {
        val sentence = when (val r = ask(client.generate(JournalPrompt.summary(text))) { it }) {
            is AiResult.Ok -> r.value
            is AiResult.Failed -> return r
        }
        val tags = ask(client.generate(JournalPrompt.tags(text)), JournalSchema::tags).valueOrNull().orEmpty()
        val mood = ask(client.generate(JournalPrompt.mood(text)), JournalSchema::mood).valueOrNull()
        return AiResult.Ok(Summary(sentence, tags, mood), ref)
    }

    override suspend fun caption(image: File): AiResult<String> =
        ask(client.generate(JournalPrompt.CAPTION, image)) { it }

    private fun <T> ask(reply: NanoReply, parse: (String) -> T?): AiResult<T> = when (reply) {
        is NanoReply.Text -> parse(reply.text)?.let { AiResult.Ok(it, ref) } ?: failed(AiError.InvalidOutput)
        is NanoReply.Failure -> failed(reply.failure.toError())
    }

    private fun failed(error: AiError) = AiResult.Failed(error, listOf(ref))

    companion object {
        const val ID = "gemini-nano"

        /** How each Nano failure is treated by the router. */
        fun NanoFailure.toError(): AiError = when (this) {
            NanoFailure.Busy -> AiError.Busy
            NanoFailure.QuotaExceeded -> AiError.RateLimited
            NanoFailure.BackgroundBlocked -> AiError.NeedsForeground
            NanoFailure.NotAvailable -> AiError.Unavailable("Gemini Nano unavailable on this device")
            NanoFailure.TooLarge -> AiError.Unavailable("Too long for the on-device model")
            NanoFailure.Other -> AiError.Unavailable("Gemini Nano gave no answer")
        }
    }
}
