package app.cove.companion.resilience

import java.io.IOException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseGuardTest {
    private class SQLiteNotADatabaseException(m: String) : RuntimeException(m)
    private class SQLiteFullException(m: String) : RuntimeException(m)
    private class KeyPermanentlyInvalidatedException : RuntimeException("gone")

    private fun check(t: Throwable?) = DatabaseGuard({ if (t != null) throw t }).check()

    @Test fun okWhenOpeningWorks() = assertEquals(DbCheck.Ok, check(null))

    @Test fun missingKeyFileIsKeyMissing() = assertEquals(DbCheck.KeyMissing, check(KeyFileMissingException()))

    @Test fun lostKeystoreKeyIsKeyMissing() {
        assertEquals(DbCheck.KeyMissing, check(KeystoreKeyMissingException()))
        assertEquals(DbCheck.KeyMissing, check(KeyPermanentlyInvalidatedException()))
    }

    @Test fun garbageKeyIsKeyInvalid() = assertEquals(DbCheck.KeyInvalid, check(AEADBadTagException("Tag mismatch")))

    @Test fun notADatabaseIsCorrupt() {
        assertEquals(DbCheck.Corrupt, check(SQLiteNotADatabaseException("file is not a database (code 26)")))
        assertEquals(DbCheck.Corrupt, check(IllegalStateException("x", RuntimeException("database disk image is malformed"))))
    }

    @Test fun failedUpgradeIsMigrationFailedUnlessMoreSpecific() {
        assertEquals(DbCheck.MigrationFailed, check(MigrationStageException(IOException("copy failed"))))
        assertEquals(DbCheck.MigrationFailed, check(IllegalStateException("A migration from 5 to 6 was required but not found")))
        assertEquals(DbCheck.Corrupt, check(MigrationStageException(SQLiteNotADatabaseException("file is not a database"))))
        assertEquals(DbCheck.StorageFull, check(MigrationStageException(IOException("write failed: ENOSPC (No space left on device)"))))
    }

    @Test fun fullDiskIsStorageFull() {
        assertEquals(DbCheck.StorageFull, check(SQLiteFullException("database or disk is full")))
    }

    @Test fun anythingElseIsUnknownAndNeverThrows() = assertEquals(DbCheck.Unknown, check(IllegalArgumentException("boom")))

    @Test fun failureIsReportedToTheNote() {
        var seen: Throwable? = null
        DatabaseGuard({ throw KeyFileMissingException() }, { seen = it }).check()
        assertEquals(KeyFileMissingException::class, seen!!::class)
    }

    @Test fun databaseProblemWinsOverCrashLoop() {
        assertNull(SafeMode.reason(DbCheck.Ok, crashLoop = false))
        assertEquals(RecoveryReason.CrashLoop, SafeMode.reason(DbCheck.Ok, crashLoop = true))
        assertEquals(RecoveryReason.Corrupt, SafeMode.reason(DbCheck.Corrupt, crashLoop = true))
        assertEquals(RecoveryReason.KeyMissing, SafeMode.reason(DbCheck.KeyMissing, crashLoop = false))
    }
}
