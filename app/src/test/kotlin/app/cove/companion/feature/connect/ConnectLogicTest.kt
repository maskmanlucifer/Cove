package app.cove.companion.feature.connect

import app.cove.companion.data.auth.SignInOutcome
import app.cove.companion.data.config.Credentials
import app.cove.companion.data.config.TestResult
import app.cove.companion.data.drive.DriveFailure
import app.cove.companion.data.drive.DriveToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectLogicTest {
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2ln"
    private fun facts(c: Credentials = Credentials(), signedIn: Boolean = false, drive: Boolean = false, tests: Map<ServiceId, TestResult> = emptyMap()) =
        ConnectFacts(c, signedIn, drive, tests)

    @Test fun everythingNotSetByDefault() = ServiceId.entries.forEach { assertEquals(ServiceStatus.NotSet, statusOf(it, facts())) }

    @Test fun statusProgression() {
        val c = Credentials(supabaseUrl = "https://a.supabase.co", supabaseAnonKey = jwt)
        assertEquals(ServiceStatus.Saved, statusOf(ServiceId.Supabase, facts(c)))
        assertEquals(ServiceStatus.Connected, statusOf(ServiceId.Supabase, facts(c, tests = mapOf(ServiceId.Supabase to TestResult(true, "")))))
        assertEquals(ServiceStatus.NeedsAttention, statusOf(ServiceId.Supabase, facts(c, tests = mapOf(ServiceId.Supabase to TestResult(false, "")))))
        assertEquals(ServiceStatus.NeedsAttention, statusOf(ServiceId.Gemini, facts(Credentials(geminiApiKey = "bad"))))
        assertEquals(ServiceStatus.Connected, statusOf(ServiceId.Drive, facts(signedIn = true, drive = true)))
        assertEquals(ServiceStatus.NotSet, statusOf(ServiceId.Drive, facts(drive = true)))
    }

    @Test fun messagesArePlainLanguage() {
        assertTrue(signInResult(SignInOutcome.Success, "a@b.c").ok)
        assertTrue(signInResult(SignInOutcome.Rejected, null).message.contains("Google provider"))
        assertFalse(driveResult(DriveToken.Failed(DriveFailure.UnknownApp)).ok)
        assertTrue(driveResult(DriveToken.Failed(DriveFailure.UnknownApp)).message.contains("SHA-1"))
    }

    @Test fun fingerprintFormatAndLinks() {
        assertEquals("0A:FF", fingerprint(byteArrayOf(10, -1)))
        assertEquals("https://supabase.com/dashboard/project/abcd/settings/api", ConnectLinks.supabaseApi("https://abcd.supabase.co/"))
        assertEquals(ConnectLinks.SUPABASE_DASHBOARD, ConnectLinks.supabaseApi(""))
    }
}
