package app.cove.companion.security

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cove.companion.data.local.CoveDatabase
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * Builds the Room database without touching the Keystore or the file: the passphrase and the one-time plaintext
     * migration run in [DeferredFactory.prepare] (off the main thread) or, at the latest, on the first real query.
     */
    fun open(context: Context, factory: DeferredFactory = DeferredFactory(context)): CoveDatabase = CoveDatabase.create(context, factory)
}

/** Open-helper factory that postpones passphrase lookup and plaintext migration until [prepare] or the first open. */
class DeferredFactory(private val context: Context) : SupportSQLiteOpenHelper.Factory {
    /** True while an old plaintext database is being encrypted. */
    val migrating = MutableStateFlow(false)

    private val real = lazy {
        val pass = EncryptedDatabase.passphrase(context)
        val file = EncryptedDatabase.file(context)
        file.parentFile?.mkdirs()
        migrating.value = SqliteHeader.isPlaintext(file)
        PlaintextMigrator(file, SqlCipherCopier).migrateIfNeeded(pass)
        migrating.value = false
        SupportOpenHelperFactory(pass.toByteArray(Charsets.US_ASCII))
    }

    /** Blocks until the key exists and any migration is done; call from a background thread. */
    fun prepare() {
        real.value
    }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredHelper(configuration, real)
}

private class DeferredHelper(
    private val config: SupportSQLiteOpenHelper.Configuration,
    private val factory: Lazy<SupportOpenHelperFactory>,
) : SupportSQLiteOpenHelper {
    private var wal: Boolean? = null
    private val helper by lazy { factory.value.create(config).also { h -> wal?.let(h::setWriteAheadLoggingEnabled) } }

    override val databaseName: String? get() = config.name
    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        if (factory.isInitialized()) helper.setWriteAheadLoggingEnabled(enabled) else wal = enabled
    }
    override val writableDatabase: SupportSQLiteDatabase get() = helper.writableDatabase
    override val readableDatabase: SupportSQLiteDatabase get() = helper.readableDatabase
    override fun close() {
        if (factory.isInitialized()) helper.close()
    }
}
