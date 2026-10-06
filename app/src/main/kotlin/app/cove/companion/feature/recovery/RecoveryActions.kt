package app.cove.companion.feature.recovery

import android.content.Context
import android.content.Intent
import android.os.Process
import app.cove.companion.data.backup.Backup
import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.wipe.DeviceWipe
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.security.EncryptedDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * File-level recovery work. Nothing here opens the database or needs a credential, so it works when both are broken.
 */
class RecoveryActions(private val context: Context) {
    private val app = context.applicationContext

    /** Files that make up the database: the main file plus SQLite's sidecars. */
    private fun dbFiles(): List<File> {
        val db = EncryptedDatabase.file(app)
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal")).filter { it.isFile }
    }

    /**
     * Writes a zip with the raw (still encrypted) database, the technical notes and the journal photos and voice notes
     * to [out], so nothing is lost even if the next step deletes local data.
     */
    fun writeCopy(out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putFile("README.txt", README.toByteArray())
            dbFiles().forEach { zip.putFile(it.name, it) }
            val notes = CrashHandler.storeFor(app).all().joinToString("\n\n") { it.toReadableText() }
            if (notes.isNotBlank()) zip.putFile("technical-details.txt", notes.toByteArray())
            File(app.filesDir, "journal").walkTopDown().filter { it.isFile }.forEach { f ->
                zip.putFile("journal/" + f.relativeTo(File(app.filesDir, "journal")).path, f)
            }
        }
    }

    private fun ZipOutputStream.putFile(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    /** Reads and checks a backup file the user picked; throws [app.cove.companion.data.backup.BackupFormatException] if it is not one. */
    fun readBackup(input: InputStream): Backup = ExportBuilder.parse(input.use { it.readBytes() })

    /**
     * Sets the unreadable database and its key aside (nothing is deleted) and stages [backup] to be imported into the
     * fresh database on the next start. The caller then restarts the app.
     */
    fun stageRestore(backupBytes: ByteArray) {
        val stash = File(app.filesDir, "recovery-old/${System.currentTimeMillis()}").apply { mkdirs() }
        (dbFiles() + listOf(EncryptedDatabase.keyFile(app)).filter { it.isFile }).forEach { it.renameTo(File(stash, it.name)) }
        PendingRestore.file(app).apply { parentFile?.mkdirs() }.writeBytes(backupBytes)
    }

    /**
     * "Start fresh": the same full reset as Me > Clear all data. Stops alarms and jobs and records the `pending_wipe`
     * marker; the files, preferences, credentials and Keystore keys are removed at the start of the next process
     * (see [DeviceWipe]), so the reset is finished even if the app dies before [restart] completes.
     */
    fun wipe() = DeviceWipe.request(app, restart = false)

    /**
     * Starts Cove again from scratch. A helper activity in its own process launches the main screen and then this
     * process ends, so the new start never races with the dying one.
     */
    fun restart() {
        app.startActivity(
            Intent(app, RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RestartActivity.EXTRA_PID, Process.myPid()),
        )
    }

    private companion object {
        const val README = "This is a copy of Cove's data from your phone.\n\n" +
            "cove.db is encrypted. It can only be opened with the key that lives in your phone's secure hardware, " +
            "so this file is for safekeeping or for repair, not for opening on a computer.\n" +
            "The journal folder holds your photos and voice notes as they are.\n"
    }
}
