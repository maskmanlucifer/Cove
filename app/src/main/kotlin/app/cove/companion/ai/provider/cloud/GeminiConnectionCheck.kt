package app.cove.companion.ai.provider.cloud

import app.cove.companion.ai.model.CloudCheck

/** Plain-language verdicts for the Gemini "Test connection" button. Never contains keys or raw error text. */
object GeminiConnectionCheck {
    /** One tiny call through [client]. */
    suspend fun run(client: GeminiDirectClient): CloudCheck = result(client.ping())

    /** Verdict for a Gemini ping. */
    fun result(reply: GeminiReply): CloudCheck = when (reply) {
        is GeminiReply.Text -> CloudCheck(true, "Gemini answered. Your key works.")
        is GeminiReply.Failure -> CloudCheck(false, problem(reply.error))
    }

    /** What went wrong, in words. */
    fun problem(error: GeminiError): String = when (error) {
        GeminiError.InvalidKey -> "Google says this API key is not valid. Copy it again from AI Studio."
        GeminiError.KeyTypeUnsupported ->
            "Google rejected this key type for Gemini. In Google Cloud console, Credentials, create an API key limited to the Generative Language API and paste that one."
        GeminiError.Forbidden -> "This key is not allowed to use Gemini. Check its restrictions in AI Studio."
        GeminiError.Quota -> "The key works, but its quota is used up for now. Try again later."
        GeminiError.ModelNotFound -> "Google does not offer that model to this key (older models are retired for new accounts). Open Advanced and use gemini-3.5-flash-lite."
        GeminiError.BadRequest -> "Gemini did not accept the request. Check the model name in Advanced."
        GeminiError.Offline -> "Cannot reach Gemini. Check your internet connection."
        GeminiError.Timeout -> "Gemini took too long to answer. Try again."
        GeminiError.Server -> "Gemini is having trouble right now. Try again in a minute."
        GeminiError.BadResponse -> "Gemini answered in a way Cove could not read. Try again."
    }
}
