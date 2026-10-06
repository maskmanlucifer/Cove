package app.cove.companion.ai

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.ai.model.Sensitivity
import app.cove.companion.ai.prompt.PromptLimits

/**
 * Every rule about who may see what, in one place (PLAN 6, "AI routing rule"). The router asks [check] before
 * touching a provider, so a provider never has to know about privacy.
 *
 * 1. Cloud providers never see [Sensitivity.Journal] requests, nor transcripts over [PromptLimits.CLOUD_TRANSCRIPT_CHARS].
 * 2. Native language models run only while the app is on screen ([Capability.needsForeground]).
 * 3. Cloud providers need a network; a credential is checked through the provider's own availability.
 */
class AiPolicy(private val foreground: ForegroundState, private val online: () -> Boolean) {
    /**
     * Why [provider] must not serve this request, or null when it may.
     *
     * @param payloadChars length of the free text that would be sent, for the cloud size cap.
     */
    fun check(provider: ProviderRef, capability: Capability, sensitivity: Sensitivity, payloadChars: Int = 0): AiError? = when {
        provider.location == Location.Cloud && sensitivity == Sensitivity.Journal -> AiError.PrivacyBlocked
        provider.location == Location.Cloud && capability == Capability.Intent && payloadChars > PromptLimits.CLOUD_TRANSCRIPT_CHARS ->
            AiError.PrivacyBlocked
        provider.location == Location.Native && capability.needsForeground && !foreground.isForeground() -> AiError.NeedsForeground
        provider.location == Location.Cloud && !online() -> AiError.Offline
        else -> null
    }
}
