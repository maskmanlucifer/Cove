package app.cove.companion.resilience

import java.io.IOException

/** Outcome of the startup database check. */
enum class DbCheck {
    Ok,

    /** The encrypted database exists but its key file is gone or the Keystore key was invalidated. */
    KeyMissing,

    /** The key file exists but cannot be unsealed (damaged or tampered with). */
    KeyInvalid,

    /** The database file is damaged. */
    Corrupt,

    /** The one-time upgrade (plaintext encryption or schema migration) failed. */
    MigrationFailed,

    /** The phone ran out of storage. */
    StorageFull,
    Unknown,
}

/** Thrown instead of creating a new key when an encrypted database already exists: a new key would brick its data. */
class KeyFileMissingException : IOException("Database key file is missing")

/** Thrown when the sealed key file exists but the Keystore key that sealed it is gone. */
class KeystoreKeyMissingException : IOException("Keystore key is missing")

/** Wraps a failure of the one-time upgrade so it can be told apart from a key or corruption problem. */
class MigrationStageException(cause: Throwable) : IOException("Database upgrade failed", cause)

/**
 * Opens the database once, off the main thread, and turns any failure into a [DbCheck] instead of a crash.
 *
 * @param open prepares the key, runs upgrades and opens the file (it must throw on any problem).
 * @param onFailure receives the raw throwable for the crash note.
 */
class DatabaseGuard(private val open: () -> Unit, private val onFailure: (Throwable) -> Unit = {}) {
    /** Runs the check; never throws. */
    fun check(): DbCheck = try {
        open()
        DbCheck.Ok
    } catch (t: Throwable) {
        runCatching { onFailure(t) }
        classify(t)
    }

    companion object {
        /** Maps [t] and its causes to the most specific [DbCheck]. Matching is by name so it works for any SQLite binding. */
        fun classify(t: Throwable): DbCheck {
            val chain = generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(12).toList()
            fun anyName(vararg parts: String) = chain.any { e -> parts.any { e.javaClass.name.contains(it, ignoreCase = true) } }
            fun anyMessage(vararg parts: String) = chain.any { e -> e.message?.let { m -> parts.any { m.contains(it, ignoreCase = true) } } == true }
            return when {
                anyName("SQLiteFull") || anyMessage("no space left", "ENOSPC", "disk is full", "database or disk is full") -> DbCheck.StorageFull
                chain.any { it is KeyFileMissingException || it is KeystoreKeyMissingException } || anyName("KeyPermanentlyInvalidated") -> DbCheck.KeyMissing
                anyName("AEADBadTag", "BadPadding", "KeyStoreException", "UnrecoverableKey", "InvalidKey", "IllegalBlockSize", "InvalidAlgorithmParameter") -> DbCheck.KeyInvalid
                anyName("NotADatabase", "DatabaseCorrupt") || anyMessage("file is not a database", "malformed", "database disk image", "corrupt") -> DbCheck.Corrupt
                chain.any { it is MigrationStageException } || anyMessage("migration") -> DbCheck.MigrationFailed
                else -> DbCheck.Unknown
            }
        }
    }
}

/** Why the Recovery screen is shown instead of the app. */
enum class RecoveryReason {
    KeyMissing, KeyInvalid, Corrupt, MigrationFailed, StorageFull, Unknown,

    /** The database is fine but the app crashed repeatedly in the last minute (safe mode). */
    CrashLoop,
}

/** Startup state of the app; the UI shows the app only in [Ready]. */
sealed interface StartupState {
    data object Checking : StartupState
    data object Ready : StartupState
    data class Recovery(val reason: RecoveryReason) : StartupState
}

/** Decides between the app and the Recovery screen. */
object SafeMode {
    /** A database problem always wins (it is the real cause); otherwise a crash loop; otherwise null (start normally). */
    fun reason(check: DbCheck, crashLoop: Boolean): RecoveryReason? = when (check) {
        DbCheck.Ok -> if (crashLoop) RecoveryReason.CrashLoop else null
        DbCheck.KeyMissing -> RecoveryReason.KeyMissing
        DbCheck.KeyInvalid -> RecoveryReason.KeyInvalid
        DbCheck.Corrupt -> RecoveryReason.Corrupt
        DbCheck.MigrationFailed -> RecoveryReason.MigrationFailed
        DbCheck.StorageFull -> RecoveryReason.StorageFull
        DbCheck.Unknown -> RecoveryReason.Unknown
    }
}
