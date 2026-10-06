package app.cove.companion.security

import app.cove.companion.resilience.DatabaseGuard
import app.cove.companion.resilience.DbCheck
import app.cove.companion.resilience.KeyFileMissingException
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeyNeverRegeneratedTest {
    @get:Rule val tmp = TemporaryFolder()

    private object Identity : Sealer {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(box: ByteArray) = box
    }

    private fun encryptedDb() = File(tmp.root, "cove.db").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }

    @Test fun existingEncryptedDatabaseForbidsCreatingAKey() {
        assertFalse(EncryptedDatabase.mayCreateKey(encryptedDb()))
    }

    @Test fun freshInstallAndPlaintextUpgradeMayCreateAKey() {
        assertTrue(EncryptedDatabase.mayCreateKey(File(tmp.root, "none.db")))
        val plain = File(tmp.root, "plain.db").apply { writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100)) }
        assertTrue(EncryptedDatabase.mayCreateKey(plain))
        assertTrue(EncryptedDatabase.mayCreateKey(File(tmp.root, "empty.db").apply { createNewFile() }))
    }

    @Test fun missingKeyWithEncryptedDatabaseThrowsAndWritesNothing() {
        val db = encryptedDb()
        val keyFile = File(tmp.root, "no_backup/cove.key")
        val pass = DatabasePassphrase(keyFile, Identity)
        assertThrows(KeyFileMissingException::class.java) { pass.get(allowCreate = EncryptedDatabase.mayCreateKey(db)) }
        assertFalse(keyFile.exists())
        assertFalse(File(keyFile.path + ".tmp").exists())
        val check = DatabaseGuard({ pass.get(allowCreate = EncryptedDatabase.mayCreateKey(db)) }).check()
        assertTrue(check == DbCheck.KeyMissing)
    }

    @Test fun freshInstallStillCreatesTheKeyOnce() {
        val keyFile = File(tmp.root, "no_backup/cove.key")
        val pass = DatabasePassphrase(keyFile, Identity)
        val first = pass.get(allowCreate = true)
        assertTrue(keyFile.exists())
        assertTrue(first == pass.get(allowCreate = false))
    }
}
