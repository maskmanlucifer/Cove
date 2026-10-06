package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ParsedIntents
import app.cove.companion.ai.provider.IntentProvider

/** The deterministic parser as a provider: exact, instant, free and always local, so it is asked first. */
class RuleIntentProvider(private val rules: RuleParser) : IntentProvider {
    override val id = "rules"
    override val location = Location.Rules

    override suspend fun availability() = Availability.Available

    override suspend fun parse(request: IntentRequest): AiResult<ParsedIntents> =
        rules.parse(request.transcript, request.context.habits).takeIf { it.isNotEmpty() }
            ?.let { AiResult.Ok(ParsedIntents(it), ref) }
            ?: AiResult.Failed(AiError.Unavailable("No rule matched"), listOf(ref))
}
