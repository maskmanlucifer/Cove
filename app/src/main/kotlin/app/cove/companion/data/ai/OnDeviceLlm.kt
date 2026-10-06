package app.cove.companion.data.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.CancellationException

/**
 * On-device language model (Gemini Nano). Inference only runs while the app is the top foreground app,
 * so callers must check that before calling [generate].
 */
interface OnDeviceLlm {
    /** True when the model is installed and ready right now. */
    suspend fun isAvailable(): Boolean

    /** Runs [prompt] and returns the raw text, or null on any failure (busy, quota, unsupported). */
    suspend fun generate(prompt: String): String?
}

/** [OnDeviceLlm] backed by the ML Kit GenAI Prompt API (Gemini Nano via AICore). */
class MlKitOnDeviceLlm : OnDeviceLlm {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun isAvailable(): Boolean = try {
        model.checkStatus() == FeatureStatus.AVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** BUSY and quota errors surface as exceptions; they mean "use the next layer", never "retry in a loop". */
    override suspend fun generate(prompt: String): String? = try {
        model.generateContent(prompt).candidates.firstOrNull()?.text
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
