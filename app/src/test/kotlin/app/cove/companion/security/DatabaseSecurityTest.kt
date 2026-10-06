package app.cove.companion.security

import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** JVM stand-in for the Keystore-backed [SecretBox]: same AES-256-GCM `iv || ciphertext` format. */
private class JvmSealer(private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : Sealer {
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(box: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, box, 0, 12)) }
        return c.doFinal(box, 12, box.size - 12)
    }
}

/** Records calls instead of touching SQLCipher; "encrypts" by writing a marker file with the source's bytes. */
private class FakeCopier(var countsAfter: Map<String, Long>? = null) : DatabaseCopier {
    val counts = mapOf("todos" to 3L, "alarms" to 1L)
    var copies = 0

    override fun copy(src: File, srcKey: String, dst: File, dstKey: String) {
        copies++
        dst.writeBytes(ByteArray(64) { 7 })
    }

    override fun tableCounts(db: File, key: String): Map<String, Long> =
        if (key.isEmpty()) counts else countsAfter ?: counts
}

class DatabaseSecurityTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cove-sec").toFile()
    }

    private fun plaintextDb(): File = File(dir, "cove.db").apply {
        writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100))
        File(dir, "cove.db-wal").writeBytes(ByteArray(8))
        File(dir, "cove.db-shm").writeBytes(ByteArray(8))
    }

    @Test
    fun passphraseIsGeneratedOnceStoredSealedAndStable() {
        val file = File(dir, "cove.key")
        val sealer = JvmSealer()
        val first = DatabasePassphrase(file, sealer).get()
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(first))
        assertEquals(first, DatabasePassphrase(file, sealer).get())
        assertNotEquals(first, DatabasePassphrase(File(dir, "other.key"), sealer).get())
    }

    @Test
    fun passphraseCannotBeReadWithAnotherKey() {
        val file = File(dir, "cove.key")
        DatabasePassphrase(file, JvmSealer()).get()
        assertThrows(Exception::class.java) { DatabasePassphrase(file, JvmSealer()).get() }
    }

    @Test
    fun sealerRoundTrips() {
        val sealer = JvmSealer()
        val data = ByteArray(40) { it.toByte() }
        assertArrayEquals(data, sealer.decrypt(sealer.encrypt(data)))
    }

    @Test
    fun headerDetectionTellsPlaintextFromEncrypted() {
        val plain = plaintextDb()
        assertTrue(SqliteHeader.isPlaintext(plain))
        assertFalse(SqliteHeader.isPlaintext(File(dir, "missing.db")))
        assertFalse(SqliteHeader.isPlaintext(File(dir, "enc.db").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }))
        assertFalse(SqliteHeader.isPlaintext(File(dir, "tiny.db").apply { writeBytes(ByteArray(3)) }))
    }

    @Test
    fun migrationReplacesPlaintextAndRemovesSidecars() {
        val db = plaintextDb()
        val copier = FakeCopier()
        assertEquals(PlaintextMigrator.Result.Migrated, PlaintextMigrator(db, copier).migrateIfNeeded("k"))
        assertFalse(SqliteHeader.isPlaintext(db))
        assertEquals(64, db.length())
        assertFalse(File(dir, "cove.db-wal").exists())
        assertFalse(File(dir, "cove.db-shm").exists())
        assertFalse(File(dir, "cove.db.enc").exists())
    }

    @Test
    fun migrationIsSkippedForEncryptedOrMissingFiles() {
        val copier = FakeCopier()
        assertEquals(PlaintextMigrator.Result.NotNeeded, PlaintextMigrator(File(dir, "cove.db"), copier).migrateIfNeeded("k"))
        File(dir, "cove.db").writeBytes(ByteArray(4096) { (it * 31).toByte() })
        assertEquals(PlaintextMigrator.Result.NotNeeded, PlaintextMigrator(File(dir, "cove.db"), copier).migrateIfNeeded("k"))
        assertEquals(0, copier.copies)
    }

    @Test
    fun migrationKeepsPlaintextWhenRowCountsDiffer() {
        val db = plaintextDb()
        val copier = FakeCopier(countsAfter = mapOf("todos" to 2L, "alarms" to 1L))
        assertThrows(IllegalStateException::class.java) { PlaintextMigrator(db, copier).migrateIfNeeded("k") }
        assertTrue(SqliteHeader.isPlaintext(db))
    }

    @Test
    fun leftoverTempFromACrashedRunIsDiscarded() {
        val db = plaintextDb()
        File(dir, "cove.db.enc").writeBytes(ByteArray(5))
        assertEquals(PlaintextMigrator.Result.Migrated, PlaintextMigrator(db, FakeCopier()).migrateIfNeeded("k"))
        assertEquals(64, db.length())
    }
}
