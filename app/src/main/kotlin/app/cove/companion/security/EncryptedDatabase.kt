package app.cove.companion.security

import android.content.Context
import app.cove.companion.data.local.CoveDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Opens `cove.db` encrypted with SQLCipher. The key does not depend on the app lock on purpose: alarms, boot
 * receivers, widgets and WorkManager must read the database while the UI is locked (see `docs/SECURITY.md`).
 */
object EncryptedDatabase {
    private const val KEY_ALIAS = "cove_db_key"

    /** Database file of [context]. */
    fun file(context: Context): File = context.getDatabasePath(CoveDatabase.NAME)

    /** The stored passphrase for [context], created on first use. */
    fun passphrase(context: Context): String =
        DatabasePassphrase(File(context.noBackupFilesDir, "cove.key"), SecretBox(KEY_ALIAS)).get()

    /** Migrates a plaintext database from an older build if present, then builds the Room database. */
    fun open(context: Context): CoveDatabase {
        val pass = passphrase(context)
        file(context).parentFile?.mkdirs()
        PlaintextMigrator(file(context), SqlCipherCopier).migrateIfNeeded(pass)
        return CoveDatabase.create(context, SupportOpenHelperFactory(pass.toByteArray(Charsets.US_ASCII)))
    }
}
