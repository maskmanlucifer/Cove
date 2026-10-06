package app.cove.companion.data.config

import java.net.URI

/** Friendly format checks for pasted credentials. A blank value is always fine: it means "not set". */
object CredentialValidator {
    private const val GOOGLE_SUFFIX = ".apps.googleusercontent.com"
    private val modelName = Regex("[A-Za-z0-9._-]+")

    /** Trims whitespace and surrounding quotes; the URL also loses trailing slashes and a model loses `models/`. */
    fun normalize(field: CredentialField, raw: String): String {
        val v = raw.trim().trim('"', '\'', '`').trim()
        return when (field) {
            CredentialField.SupabaseUrl -> v.trimEnd('/')
            CredentialField.GeminiModel, CredentialField.GeminiFallbackModel -> v.removePrefix("models/")
            else -> v
        }
    }

    /** A plain-language problem with [raw] for [field], or null when it looks right. */
    fun check(field: CredentialField, raw: String): String? {
        val v = normalize(field, raw)
        if (v.isEmpty()) return null
        return when (field) {
            CredentialField.SupabaseUrl -> url(v)
            CredentialField.SupabaseAnonKey -> anonKey(v)
            CredentialField.GoogleWebClientId -> clientId(v)
            CredentialField.GeminiApiKey -> geminiKey(v)
            CredentialField.GeminiModel, CredentialField.GeminiFallbackModel ->
                if (modelName.matches(v)) null else "Model names look like gemini-2.5-flash-lite."
        }
    }

    /** All problems in [credentials], by field. */
    fun check(credentials: Credentials): Map<CredentialField, String> =
        CredentialField.entries.mapNotNull { f -> check(f, credentials[f])?.let { f to it } }.toMap()

    private fun url(v: String): String? {
        if (v.any(Char::isWhitespace)) return "The URL should not contain spaces."
        val uri = runCatching { URI(v) }.getOrNull()
        val host = uri?.host
        return when {
            uri == null || uri.scheme == null -> "Start with https://, like https://abcdwxyz.supabase.co"
            !uri.scheme.equals("https", ignoreCase = true) -> "Use the https:// address."
            host.isNullOrBlank() -> "That does not look like a web address."
            host == "supabase.com" || host == "app.supabase.com" ->
                "That is the dashboard address. You need the Project URL, which ends in .supabase.co"
            host.endsWith(".supabase.co") && !uri.path.isNullOrEmpty() ->
                "Paste just the project URL, like https://abcdwxyz.supabase.co"
            else -> null
        }
    }

    private fun anonKey(v: String): String? {
        if (v.any(Char::isWhitespace)) return "The key should be one line with no spaces."
        if (v.startsWith("sb_secret_")) return SERVICE_KEY_MESSAGE
        if (v.startsWith("sb_publishable_")) return null
        val parts = v.split('.')
        val isJwt = parts.size == 3 && v.startsWith("eyJ") && parts.all { p -> p.isNotEmpty() && p.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' } }
        if (!isJwt) return "That does not look like the anon key. It is a long text starting with eyJ."
        return if (jwtRole(parts[1]) == "service_role") SERVICE_KEY_MESSAGE else null
    }

    private fun clientId(v: String): String? = when {
        v.any(Char::isWhitespace) -> "The client ID should not contain spaces."
        v.startsWith("GOCSPX-") -> "That is the client secret. Paste the Client ID instead, it ends in .apps.googleusercontent.com"
        !v.endsWith(GOOGLE_SUFFIX) || v.length == GOOGLE_SUFFIX.length -> "A web client ID ends in .apps.googleusercontent.com"
        else -> null
    }

    private fun geminiKey(v: String): String? = when {
        v.any(Char::isWhitespace) -> "The key should be one line with no spaces."
        !(v.startsWith("AIza") || v.startsWith("AQ.")) || v.length < 30 -> "A Gemini API key starts with AQ. (new keys) or AIza (older keys)."
        else -> null
    }

    /** `role` claim of a JWT payload, read without verifying (only used to warn about the wrong key). */
    private fun jwtRole(payload: String): String? = runCatching {
        val bytes = java.util.Base64.getUrlDecoder().decode(payload.trimEnd('='))
        Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(String(bytes))?.groupValues?.get(1)
    }.getOrNull()

    private const val SERVICE_KEY_MESSAGE =
        "That is the secret service key. Use the anon public key instead; the secret one must never go in an app."
}
