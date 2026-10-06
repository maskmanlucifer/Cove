package app.cove.companion.ai

import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.provider.AdviceProvider
import app.cove.companion.ai.provider.AiProvider
import app.cove.companion.ai.provider.BriefProvider
import app.cove.companion.ai.provider.CaptionProvider
import app.cove.companion.ai.provider.CategoryProvider
import app.cove.companion.ai.provider.EmbeddingProvider
import app.cove.companion.ai.provider.IntentProvider
import app.cove.companion.ai.provider.SpeechProvider
import app.cove.companion.ai.provider.SummaryProvider

/** Every provider, grouped by capability and in the order the router tries them. */
data class AiProviders(
    val intent: List<IntentProvider> = emptyList(),
    val brief: List<BriefProvider> = emptyList(),
    val speech: List<SpeechProvider> = emptyList(),
    val caption: List<CaptionProvider> = emptyList(),
    val summary: List<SummaryProvider> = emptyList(),
    val embedding: List<EmbeddingProvider> = emptyList(),
    val category: List<CategoryProvider> = emptyList(),
    val advice: List<AdviceProvider> = emptyList(),
) {
    /** The providers of [capability], in order. */
    fun of(capability: Capability): List<AiProvider> = when (capability) {
        Capability.Intent -> intent
        Capability.Brief -> brief
        Capability.Speech -> speech
        Capability.Caption -> caption
        Capability.Summary -> summary
        Capability.Embedding -> embedding
        Capability.Category -> category
        Capability.Advice -> advice
    }
}
