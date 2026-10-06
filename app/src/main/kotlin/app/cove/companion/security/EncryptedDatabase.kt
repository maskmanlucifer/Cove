package app.cove.companion.security

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.resilience.MigrationStageException
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

    /** Key file of [context]. */
    fun keyFile(context: Context): File = File(context.noBackupFilesDir, "cove.key")

    /** A new key may only be created while no encrypted database exists; otherwise it would orphan that data. */
    fun mayCreateKey(dbFile: File): Boolean = !SqliteHeader.isEncrypted(dbFile)

    /** The stored passphrase for [context]; created on first use unless an encrypted database already exists (then it throws). */
    fun passphrase(context: Context): String =
        DatabasePassphrase(keyFile(context), SecretBox(KEY_ALIAS)).get(allowCreate = mayCreateKey(file(context)))

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
        try {
            PlaintextMigrator(file, SqlCipherCopier).migrateIfNeeded(pass)
        } catch (t: Throwable) {
            throw MigrationStageException(t)
        } finally {
            migrating.value = false
        }
        SupportOpenHelperFactory(pass.toByteArray(Charsets.US_ASCII))
    }

    /** Blocks until the key exists and any migration is done; call from a background thread. */
    fun prepare() {
        real.value
    }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredHelper(withoutAutoDelete(configuration), real)

    /**
     * Android's default reaction to a database it thinks is corrupt is to delete the file and start empty. For
     * Cove that would silently destroy data that may only be locked or briefly unreadable, so corruption is
     * reported (and shown on the Recovery screen) but the file is never touched.
     */
    private fun withoutAutoDelete(c: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper.Configuration {
        val inner = c.callback
        val keep = object : SupportSQLiteOpenHelper.Callback(inner.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = inner.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                CrashHandler.report("db-corruption", IllegalStateException("database reported corrupt"))
            }
        }
        return SupportSQLiteOpenHelper.Configuration.builder(c.context)
            .name(c.name).callback(keep).noBackupDirectory(c.useNoBackupDirectory)
            .allowDataLossOnRecovery(false).build()
    }
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
