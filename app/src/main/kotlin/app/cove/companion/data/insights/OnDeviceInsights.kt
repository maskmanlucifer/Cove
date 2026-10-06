package app.cove.companion.data.insights

import android.graphics.Bitmap

/**
 * On-device understanding of journal content (PLAN 6a/6d). Journal text and photos never leave the phone, so every
 * implementation must run locally. Calls return null when the model is unavailable, busy or over quota; callers
 * treat that as "no insight" and never retry in a loop.
 */
interface OnDeviceInsights {
    /** True when the local model can answer right now. */
    suspend fun isAvailable(): Boolean

    /** One calm sentence summarising [text]. */
    suspend fun summarize(text: String): String?

    /** A few short lowercase tags for [text]. */
    suspend fun tags(text: String): List<String>

    /** The mood [text] reads as, one of [MOODS]. */
    suspend fun mood(text: String): String?

    /** A one-sentence description of [image]. */
    suspend fun caption(image: Bitmap): String?

    companion object {
        val MOODS = listOf("calm", "good", "tired", "low", "stressed")
    }
}

/** Fallback used when Nano is not on the device: never available, never produces anything. */
object NoOpInsights : OnDeviceInsights {
    override suspend fun isAvailable() = false
    override suspend fun summarize(text: String): String? = null
    override suspend fun tags(text: String): List<String> = emptyList()
    override suspend fun mood(text: String): String? = null
    override suspend fun caption(image: Bitmap): String? = null
}

/** Turns text into a fixed-size vector for semantic search. */
interface TextEmbedder {
    /** True when [embed] can produce vectors. */
    suspend fun isAvailable(): Boolean

    /** The embedding of [text], or null when unavailable. */
    suspend fun embed(text: String): FloatArray?
}

/** Fallback until an embedding model (EmbeddingGemma via LiteRT, PLAN 6a) is bundled: search uses keywords only. */
object NoOpEmbedder : TextEmbedder {
    override suspend fun isAvailable() = false
    override suspend fun embed(text: String): FloatArray? = null
}
