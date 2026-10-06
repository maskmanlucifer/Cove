package app.cove.companion.ai.provider.ondevice

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.provider.EmbeddingProvider

/**
 * Placeholder for the on-device embedder (EmbeddingGemma via LiteRT, PLAN 6a): Nano has no embedding API and the
 * model file is not bundled yet, so search stays keyword-only. Replace this class when the model ships.
 */
class UnbundledEmbeddingProvider : EmbeddingProvider {
    override val id = "embedding-gemma"
    override val location = Location.Native

    override suspend fun availability() = Availability.Unavailable("No embedding model is bundled yet")

    override suspend fun embed(text: String): AiResult<FloatArray> =
        AiResult.Failed(AiError.Unavailable("No embedding model is bundled yet"), listOf(ref))
}
