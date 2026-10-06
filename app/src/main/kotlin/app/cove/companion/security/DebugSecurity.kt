package app.cove.companion.security

import android.content.Context
import app.cove.companion.data.local.CoveDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Debug-only helpers for exercising the plaintext migration. */
object DebugSecurity {
    /** Closes [db], rewrites `cove.db` as plaintext (as older builds stored it) and kills the process. */
    fun downgradeToPlaintext(context: Context, db: CoveDatabase) {
        val file = EncryptedDatabase.file(context)
        val temp = File(file.path + ".plain")
        db.close()
        temp.delete()
        SqlCipherCopier.copy(file, EncryptedDatabase.passphrase(context), temp, "")
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
