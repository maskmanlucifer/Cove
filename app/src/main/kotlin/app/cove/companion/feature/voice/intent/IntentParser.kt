package app.cove.companion.feature.voice.intent

import app.cove.companion.core.Clock
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.ai.AiGateway
import app.cove.companion.data.ai.OnDeviceLlm
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the parser knows about the user's data and runtime state. */
data class ParseContext(
    val habits: List<String> = emptyList(),
    val todoCategories: List<String> = emptyList(),
    /** Nano only runs while the app is the top foreground app. */
    val foreground: Boolean = true,
    val online: Boolean = true,
)

/**
 * Layered understanding of a transcript: deterministic rules, then Gemini Nano (foreground only), then the
 * cloud gateway. Every model answer is validated by [IntentJson]; if nothing works the result is
 * [ParseOutcome.Partial]. Rules go first because they are exact, instant and free; journal notes are only
 * ever produced on-device (rules or Nano), never by the cloud, and long transcripts never leave the phone.
 */
class IntentParser(
    private val clock: Clock,
    private val llm: OnDeviceLlm? = null,
    private val gateway: AiGateway? = null,
    private val rules: RuleParser = RuleParser(clock),
) {
    suspend fun parse(transcript: String, ctx: ParseContext = ParseContext()): ParseOutcome {
        val text = transcript.trim()
        rules.parse(text, ctx.habits).takeIf { it.isNotEmpty() }?.let { return ParseOutcome.Understood(it, Layer.Rules) }
        if (text.isEmpty()) return ParseOutcome.Partial(text, emptyList())

        val now = clock.now().toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substringBeforeLast(':').take(16)
        val system = IntentJson.systemPrompt(now, ctx.todoCategories)

        if (llm != null && ctx.foreground && llm.isAvailable()) {
            llm.generate("$system\n\nCommand: $text")
                ?.let(IntentJson::parse)?.takeIf { it.isNotEmpty() }
                ?.let { return ParseOutcome.Understood(it, Layer.OnDevice) }
        }
        if (gateway != null && gateway.enabled && ctx.online && text.length <= MAX_CLOUD_CHARS) {
            gateway.parseIntent(text, now, ZoneId.systemDefault().id, ctx.todoCategories)
                ?.let(IntentJson::parse)
                ?.filterNot { it is VoiceIntent.JournalNote }
                ?.takeIf { it.isNotEmpty() }
                ?.let { return ParseOutcome.Understood(it, Layer.Cloud) }
        }
        return ParseOutcome.Partial(text, rules.guesses(text))
    }

    /** Likely readings of a half-heard transcript (frame 19). */
    fun guesses(text: String) = rules.guesses(text)

    private companion object {
        const val MAX_CLOUD_CHARS = 160
    }
}
