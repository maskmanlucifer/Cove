package app.cove.companion.data.config

/**
 * Builds the setup code of the connections saved on this phone, so a reinstall, a new phone or "Clear all data" can
 * be followed by one paste. The code holds secrets (anon key, Gemini key): never log it, use [mask] for any text
 * that might reach a log.
 */
object SetupCodeExport {
    /** The code for [saved] (blank fields are left out), or null when nothing is saved. */
    fun code(saved: Credentials): String? {
        val values = CredentialField.entries.associateWith { saved[it] }.filterValues { it.isNotBlank() }
        return if (values.isEmpty()) null else SetupCode.encode(values)
    }

    /** The text of a file holding [code]: a warning, then the code on its own line ([SetupCode.parse] ignores the words). */
    fun fileText(code: String): String =
        "Cove setup code\n" +
            "Keep this file private: anyone who has it can use your Supabase, Google and Gemini connections.\n" +
            "In Cove open Me > Connect services > Paste setup code (or Import setup code from a file).\n\n" +
            code + "\n"

    /** [text] with every setup code replaced by `cove-setup:1:<hidden>`, safe for logs and error reports. */
    fun mask(text: String): String = Regex("""cove-setup:(\d+):[A-Za-z0-9+/_=\-]+""").replace(text) { "cove-setup:${it.groupValues[1]}:<hidden>" }
}
