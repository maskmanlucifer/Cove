package app.cove.companion.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCodeExportTest {
    private val creds = Credentials(
        supabaseUrl = "https://abcdefghij.supabase.co", supabaseAnonKey = "eyJhbGciOiJIUzI1NiJ9.payload.signature",
        googleWebClientId = "123456789-abc.apps.googleusercontent.com", geminiApiKey = "AI" + "zaSyDUMMYDUMMYDUMMYDUMMYDUMMYDUMMY123",
    )

    @Test fun roundTripRestoresEveryField() {
        val code = SetupCodeExport.code(creds)!!
        val parsed = SetupCode.parse(code) as SetupCodeResult.Parsed
        assertEquals(creds.supabaseUrl, parsed.values[CredentialField.SupabaseUrl])
        assertEquals(creds.supabaseAnonKey, parsed.values[CredentialField.SupabaseAnonKey])
        assertEquals(creds.googleWebClientId, parsed.values[CredentialField.GoogleWebClientId])
        assertEquals(creds.geminiApiKey, parsed.values[CredentialField.GeminiApiKey])
    }

    @Test fun fileTextParsesBackAndWarns() {
        val text = SetupCodeExport.fileText(SetupCodeExport.code(creds)!!)
        assertTrue(text.contains("Keep this file private"))
        assertTrue(SetupCode.parse(text) is SetupCodeResult.Parsed)
    }

    @Test fun nothingSavedMeansNoCode() {
        assertNull(SetupCodeExport.code(Credentials(geminiModel = "", geminiFallbackModel = "")))
    }

    @Test fun maskHidesTheSecretsEverywhere() {
        val code = SetupCodeExport.code(creds)!!
        val masked = SetupCodeExport.mask("failed to apply $code (twice: $code)")
        assertFalse(masked.contains(code.substringAfterLast(':')))
        assertEquals("failed to apply cove-setup:1:<hidden> (twice: cove-setup:1:<hidden>)", masked)
    }
}
