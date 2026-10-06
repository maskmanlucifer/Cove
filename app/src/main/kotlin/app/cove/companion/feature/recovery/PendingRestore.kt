package app.cove.companion.feature.recovery

import android.content.Context
import app.cove.companion.AppContainer
import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.backup.Importer
import app.cove.companion.resilience.CrashHandler
import java.io.File

/** A backup file chosen on the Recovery screen, imported once the fresh database is ready on the next start. */
object PendingRestore {
    /** Where the staged backup waits. */
    fun file(context: Context) = File(context.noBackupFilesDir, "pending-restore.json.gz")

    /** Imports the staged backup, if any, and removes it whether or not it worked (so a bad file cannot loop). */
    suspend fun applyIfStaged(context: Context, container: AppContainer) {
        val staged = file(context)
        if (!staged.isFile) return
        try {
            Importer(container.backupStore()).restore(ExportBuilder.parse(staged.readBytes()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashHandler.report("pending-restore", e)
        } finally {
            staged.delete()
        }
    }
}
