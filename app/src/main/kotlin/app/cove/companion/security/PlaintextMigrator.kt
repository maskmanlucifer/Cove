package app.cove.companion.security

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Copies a whole SQLite/SQLCipher database into a new file under a different key (an empty key means plaintext). */
interface DatabaseCopier {
    /** Writes the contents of [src] (opened with [srcKey]) to [dst] encrypted with [dstKey], including `user_version`. */
    fun copy(src: File, srcKey: String, dst: File, dstKey: String)

    /** Row count of every user table in [db] opened with [key]. */
    fun tableCounts(db: File, key: String): Map<String, Long>
}

/**
 * Upgrades installs that still hold a plaintext `cove.db`: exports it into an encrypted copy, checks that every
 * table kept its row count, atomically replaces the plaintext file and removes its `-wal`/`-shm` leftovers.
 * Safe to re-run after a crash at any step; the plaintext file is only replaced once the copy is verified.
 */
class PlaintextMigrator(private val dbFile: File, private val copier: DatabaseCopier) {
    /** Outcome of [migrateIfNeeded]. */
    enum class Result { NotNeeded, Migrated }

    /** Encrypts [dbFile] with [passphrase] when it is a plaintext database. */
    fun migrateIfNeeded(passphrase: String): Result {
        val temp = File(dbFile.path + ".enc")
        deleteWithSidecars(temp)
        if (!SqliteHeader.isPlaintext(dbFile)) return Result.NotNeeded
        val before = copier.tableCounts(dbFile, "")
        copier.copy(dbFile, "", temp, passphrase)
        val after = copier.tableCounts(temp, passphrase)
        check(before == after) { "Encrypted copy does not match the original: $before vs $after" }
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
        Files.move(temp.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return Result.Migrated
    }

    private fun deleteWithSidecars(file: File) {
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
    }
}

/** Recognises plaintext SQLite files by their 16-byte magic header; encrypted files look like random bytes. */
object SqliteHeader {
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** True when [file] exists and starts with the plaintext SQLite header. */
    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.size) return false
        val head = ByteArray(MAGIC.size)
        file.inputStream().use { if (it.read(head) != head.size) return false }
        return head.contentEquals(MAGIC)
    }
}
