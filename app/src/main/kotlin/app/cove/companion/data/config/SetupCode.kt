package app.cove.companion.data.config

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Outcome of reading a setup code. */
sealed interface SetupCodeResult {
    /** The code was valid; [values] holds the fields it carries (any subset). */
    data class Parsed(val values: Map<CredentialField, String>) : SetupCodeResult

    /** The code could not be used; [message] says why in plain words. */
    data class Invalid(val message: String) : SetupCodeResult
}

/**
 * One pasteable text block, `cove-setup:1:<base64 of a JSON object>`, carrying any subset of the credential
 * fields so the owner can fill the whole app in one paste. `tools/make-setup-code.py` builds them.
 */
object SetupCode {
    private const val PREFIX = "cove-setup:"
    private val shape = Regex("""cove-setup:(\d+):([A-Za-z0-9+/_=\-\s]+)""")

    /** Reads [text] (surrounding words and line breaks are tolerated) and validates every value it carries. */
    fun parse(text: String): SetupCodeResult {
        if (text.isBlank()) return SetupCodeResult.Invalid("Paste a setup code first.")
        val match = shape.find(text.trim())
            ?: return SetupCodeResult.Invalid(
                if (PREFIX in text) "That setup code is cut off or damaged. Copy it again in full." else "That does not look like a Cove setup code. It starts with cove-setup:1:",
            )
        if (match.groupValues[1] != "1") return SetupCodeResult.Invalid("This code was made by a newer Cove. Update the app and try again.")
        val obj = decode(match.groupValues[2]) ?: return SetupCodeResult.Invalid("That setup code is cut off or damaged. Copy it again in full.")
        val values = CredentialField.entries.mapNotNull { f ->
            (obj[f.key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { f to CredentialValidator.normalize(f, it) }
        }.toMap()
        if (values.isEmpty()) return SetupCodeResult.Invalid("That setup code does not contain any settings.")
        values.forEach { (field, value) ->
            CredentialValidator.check(field, value)?.let { return SetupCodeResult.Invalid("${field.label}: $it") }
        }
        return SetupCodeResult.Parsed(values)
    }

    /** Builds a code from the non-blank [values]. */
    fun encode(values: Map<CredentialField, String>): String {
        val json = buildJsonObject { values.forEach { (f, v) -> if (v.isNotBlank()) put(f.key, v) } }
        return PREFIX + "1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    private fun decode(raw: String): JsonObject? = runCatching {
        val clean = raw.filterNot(Char::isWhitespace).replace('+', '-').replace('/', '_').trimEnd('=')
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(clean))).jsonObject
    }.getOrNull()
}
