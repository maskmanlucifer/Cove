package app.cove.companion.ai.provider.ondevice

import java.io.File

/** Install state of the on-device model. */
enum class NanoStatus { Available, Downloadable, Downloading, Unavailable }

/** Why a Nano call produced no text, independent of the ML Kit API that reported it. */
enum class NanoFailure { Busy, QuotaExceeded, BackgroundBlocked, NotAvailable, TooLarge, Other }

/** Result of one Nano call. */
sealed interface NanoReply {
    data class Text(val text: String) : NanoReply
    data class Failure(val failure: NanoFailure) : NanoReply
}

/**
 * The only door to Gemini Nano (PLAN 6a: "wrap it behind NanoClient", the ML Kit Prompt API is still beta).
 * Inference only runs while the app is the top foreground app; the router enforces that, not this client.
 */
interface NanoClient {
    suspend fun status(): NanoStatus

    /** Runs [prompt] and returns the raw text, or the reason it failed. */
    suspend fun generate(prompt: String): NanoReply

    /** Runs [prompt] over the image stored in [image]. */
    suspend fun generate(prompt: String, image: File): NanoReply
}
