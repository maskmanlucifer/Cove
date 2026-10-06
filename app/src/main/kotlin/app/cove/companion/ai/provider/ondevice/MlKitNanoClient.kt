package app.cove.companion.ai.provider.ondevice

import android.graphics.BitmapFactory
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import java.io.File

/** [NanoClient] backed by the ML Kit GenAI Prompt API (Gemini Nano via AICore). */
class MlKitNanoClient : NanoClient {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun status(): NanoStatus = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoStatus.Available
            FeatureStatus.DOWNLOADABLE -> NanoStatus.Downloadable
            FeatureStatus.DOWNLOADING -> NanoStatus.Downloading
            else -> NanoStatus.Unavailable
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        NanoStatus.Unavailable
    }

    override suspend fun generate(prompt: String): NanoReply = reply { model.generateContent(prompt).candidates.firstOrNull()?.text }

    override suspend fun generate(prompt: String, image: File): NanoReply = reply {
        val bitmap = BitmapFactory.decodeFile(image.path) ?: return@reply null
        try {
            model.generateContent(generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {}).candidates.firstOrNull()?.text
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun reply(call: suspend () -> String?): NanoReply = try {
        call()?.trim()?.takeIf { it.isNotEmpty() }?.let { NanoReply.Text(it) } ?: NanoReply.Failure(NanoFailure.Other)
    } catch (e: CancellationException) {
        throw e
    } catch (e: GenAiException) {
        NanoReply.Failure(failureOf(e.errorCode))
    } catch (e: Exception) {
        NanoReply.Failure(NanoFailure.Other)
    }

    internal companion object {
        /** Maps an ML Kit [GenAiException.ErrorCode] to a [NanoFailure]. */
        fun failureOf(code: Int): NanoFailure = when (code) {
            GenAiException.ErrorCode.BUSY -> NanoFailure.Busy
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> NanoFailure.QuotaExceeded
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> NanoFailure.BackgroundBlocked
            GenAiException.ErrorCode.NOT_AVAILABLE, GenAiException.ErrorCode.NOT_SUPPORTED,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE, GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> NanoFailure.NotAvailable
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> NanoFailure.TooLarge
            else -> NanoFailure.Other
        }
    }
}
