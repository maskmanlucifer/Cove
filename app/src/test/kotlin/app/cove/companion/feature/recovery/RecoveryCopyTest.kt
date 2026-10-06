package app.cove.companion.feature.recovery

import app.cove.companion.data.backup.RestoreRule
import app.cove.companion.feature.me.SyncAction
import app.cove.companion.feature.me.backupMessage
import app.cove.companion.feature.me.syncProblem
import app.cove.companion.data.backup.BackupResult
import app.cove.companion.resilience.RecoveryReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCopyTest {
    @Test fun everyReasonHasPlainCopyAndAWayForward() {
        RecoveryReason.entries.forEach {
            val c = recoveryCopy(it)
            assertTrue(c.actions.isNotEmpty())
            assertFalse(c.title + c.body, Regex("Exception|code \\d|sqlite|SQLCipher", RegexOption.IGNORE_CASE).containsMatchIn(c.title + c.body))
        }
    }

    @Test fun keyMissingExplainsTheCopyIsEncrypted() {
        assertTrue(recoveryCopy(RecoveryReason.KeyMissing).copyNote!!.contains("locked"))
        assertTrue(RecoveryAction.OpenStorage in recoveryCopy(RecoveryReason.StorageFull).actions)
        assertFalse(RecoveryAction.OpenStorage in recoveryCopy(RecoveryReason.Corrupt).actions)
    }

    @Test fun startFreshNeedsTheTypedWord() {
        assertFalse(StartFreshRule.confirmed(""))
        assertFalse(StartFreshRule.confirmed("delet"))
        assertTrue(StartFreshRule.confirmed(" delete "))
    }

    @Test fun restoreIgnoresSettingsAndTheOnboardingAlarmButNotRealData() {
        assertTrue(RestoreRule.isEmpty(mapOf("settings" to 1, "alarms" to 1, "todos" to 0), defaultAlarms = 1))
        assertFalse(RestoreRule.isEmpty(mapOf("settings" to 1, "alarms" to 2, "todos" to 0), defaultAlarms = 1))
        assertFalse(RestoreRule.isEmpty(mapOf("alarms" to 1, "todos" to 1), defaultAlarms = 1))
    }

    @Test fun backupFailureNeverShowsRawText() {
        val m = backupMessage(BackupResult.Failed("Cannot access database on the main thread"), restoring = false)
        assertFalse(m.contains("database"))
    }

    @Test fun syncFailuresMapToAPlainMessageAndAnAction() {
        assertEquals(SyncAction.OpenConnect, syncProblem("Signed out").action)
        assertEquals(SyncAction.Retry, syncProblem("Unable to resolve host x").action)
        assertEquals(SyncAction.OpenConnect, syncProblem("HTTP 404 relation \"todos\" does not exist").action)
        assertEquals(SyncAction.Retry, syncProblem("???").action)
    }
}
