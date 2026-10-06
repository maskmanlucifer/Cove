package app.cove.companion.data.insights

import android.graphics.Bitmap
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * [OnDeviceInsights] on Gemini Nano through the ML Kit GenAI Prompt API. Only works while the app is in the
 * foreground (the system refuses background inference), so [SearchIndexer] only calls it from the UI path.
 * Any failure (not downloaded, BUSY, quota) yields null so the app falls back to plain keyword search.
 */
class NanoInsights : OnDeviceInsights {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun isAvailable(): Boolean =
        runCatching { model.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    override suspend fun summarize(text: String): String? =
        ask("Summarise this diary entry in one calm sentence of at most 20 words. Reply with the sentence only.\n\n${text.take(MAX_CHARS)}")

    override suspend fun tags(text: String): List<String> =
        ask("Give 3 to 5 one-word lowercase topic tags for this diary entry. Reply with the tags separated by commas only.\n\n${text.take(MAX_CHARS)}")
            ?.split(',', '\n')?.map { it.trim().lowercase().trim('#', '.') }?.filter { it.isNotEmpty() }?.take(5)
            ?: emptyList()

    override suspend fun mood(text: String): String? {
        val options = OnDeviceInsights.MOODS.joinToString(", ")
        val answer = ask("Which mood does this diary entry read as: $options? Reply with one word.\n\n${text.take(MAX_CHARS)}")
        return answer?.lowercase()?.let { a -> OnDeviceInsights.MOODS.firstOrNull { a.contains(it) } }
    }

    override suspend fun caption(image: Bitmap): String? = runCatching {
        val request = generateContentRequest(ImagePart(image), TextPart("Describe this photo in one short sentence.")) {}
        model.generateContent(request).candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun ask(prompt: String): String? = runCatching {
        model.generateContent(prompt).candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private companion object {
        /** Nano takes about 4,000 tokens in; stay well under. */
        const val MAX_CHARS = 3000
    }
}
