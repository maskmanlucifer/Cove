package app.cove.companion.data.config

import kotlinx.serialization.Serializable

/** The editable credential fields; [key] is the name used in setup codes. */
enum class CredentialField(val key: String, val label: String) {
    SupabaseUrl("supabaseUrl", "Project URL"),
    SupabaseAnonKey("supabaseAnonKey", "Anon key"),
    GoogleWebClientId("googleWebClientId", "Web client ID"),
    GeminiApiKey("geminiApiKey", "Gemini API key"),
    GeminiModel("geminiModel", "Model"),
    GeminiFallbackModel("geminiFallbackModel", "Fallback model"),
}

/**
 * The user's own service credentials. Blank means "not set" and switches that feature off.
 * Models fall back to the defaults when blank, see [model] and [fallbackModel].
 */
@Serializable
data class Credentials(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val googleWebClientId: String = "",
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_MODEL,
    val geminiFallbackModel: String = DEFAULT_FALLBACK_MODEL,
) {
    /** Supabase URL and anon key are both present. */
    val hasSupabase: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    /** A Google web client ID is present. */
    val hasGoogle: Boolean get() = googleWebClientId.isNotBlank()

    /** A Gemini API key is present. */
    val hasGemini: Boolean get() = geminiApiKey.isNotBlank()

    /** The model to call first; retired defaults saved by older builds are replaced by the current one. */
    val model: String get() = geminiModel.takeUnless { it.isBlank() || it in RETIRED_MODELS } ?: DEFAULT_MODEL

    /** The model to retry on once when the first fails. */
    val fallbackModel: String get() = geminiFallbackModel.takeUnless { it.isBlank() || it in RETIRED_MODELS } ?: DEFAULT_FALLBACK_MODEL

    /** Value of [field]. */
    operator fun get(field: CredentialField): String = when (field) {
        CredentialField.SupabaseUrl -> supabaseUrl
        CredentialField.SupabaseAnonKey -> supabaseAnonKey
        CredentialField.GoogleWebClientId -> googleWebClientId
        CredentialField.GeminiApiKey -> geminiApiKey
        CredentialField.GeminiModel -> geminiModel
        CredentialField.GeminiFallbackModel -> geminiFallbackModel
    }

    /** Copy with [field] replaced. */
    fun with(field: CredentialField, value: String): Credentials = when (field) {
        CredentialField.SupabaseUrl -> copy(supabaseUrl = value)
        CredentialField.SupabaseAnonKey -> copy(supabaseAnonKey = value)
        CredentialField.GoogleWebClientId -> copy(googleWebClientId = value)
        CredentialField.GeminiApiKey -> copy(geminiApiKey = value)
        CredentialField.GeminiModel -> copy(geminiModel = value)
        CredentialField.GeminiFallbackModel -> copy(geminiFallbackModel = value)
    }

    /** Copy with every entry of [patch] applied (values are normalised first). */
    fun patched(patch: Map<CredentialField, String>): Credentials =
        patch.entries.fold(this) { acc, (field, value) -> acc.with(field, CredentialValidator.normalize(field, value)) }

    /** This, with every blank field taken from [defaults]. */
    internal fun filledBy(defaults: Credentials): Credentials = Credentials(
        supabaseUrl.ifBlank { defaults.supabaseUrl },
        supabaseAnonKey.ifBlank { defaults.supabaseAnonKey },
        googleWebClientId.ifBlank { defaults.googleWebClientId },
        geminiApiKey.ifBlank { defaults.geminiApiKey },
        geminiModel.ifBlank { defaults.geminiModel },
        geminiFallbackModel.ifBlank { defaults.geminiFallbackModel },
    )

    /** Hides every secret so the value is safe to log. */
    override fun toString(): String = "Credentials(supabase=$hasSupabase, google=$hasGoogle, gemini=$hasGemini)"

    companion object {
        /** Cheap, fast, good at short structured replies; the one model every free-tier key can use. */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

        /** Retried once when the first model fails; also a Flash-Lite model so it stays within the free tier. */
        const val DEFAULT_FALLBACK_MODEL = "gemini-3.1-flash-lite"

        /** Earlier defaults that Google no longer offers to new projects. */
        private val RETIRED_MODELS = setOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-lite")
    }
}
