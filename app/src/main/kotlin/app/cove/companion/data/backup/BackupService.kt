package app.cove.companion.data.backup

import app.cove.companion.data.drive.DriveClient
import app.cove.companion.data.drive.DriveException
import app.cove.companion.data.sync.SyncTables
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Outcome of a backup or restore, shaped for both the worker and the Me sheet. */
sealed interface BackupResult {
    /** Finished; [detail] is the file name for a backup or the row count text for a restore. */
    data class Done(val detail: String) : BackupResult
    data object NeedsConsent : BackupResult
    data object Offline : BackupResult
    data object NothingToRestore : BackupResult
    data object NotEmpty : BackupResult
    data class Failed(val reason: String, val cause: Throwable? = null) : BackupResult
}

/** Writes monthly snapshots to `Cove/Backups` on Drive, prunes old ones, and restores the newest. */
class BackupService(
    private val store: BackupStore,
    private val drive: DriveClient,
    private val now: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val tempFile: () -> File,
) {
    /** Exports all synced tables and uploads `cove-YYYY-MM.json.gz` plus journal Markdown, then applies retention. */
    suspend fun backUp(): BackupResult = guarded {
        val time = now()
        val month = YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))
        val tables = SyncTables.all.associate { it.name to store.readAll(it) }
        val json = ExportBuilder.snapshotJson(time, tables) { name -> SyncTables.find(name)?.key ?: "id" }
        val folder = drive.folders().backups
        val name = ExportBuilder.snapshotName(month)
        drive.upload(folder, name, "application/gzip", ExportBuilder.gzip(json))

        val existing = drive.list(folder).map { it.name }.toSet()
        ExportBuilder.journalMarkdown(tables["journal_entries"].orEmpty()).forEach { (m, text) ->
            val file = ExportBuilder.journalName(m)
            if (m == month || file !in existing) drive.upload(folder, file, "text/markdown", text.toByteArray())
        }
        Retention.toDelete(drive.list(folder)).forEach { drive.delete(it.id) }
        BackupResult.Done(name)
    }

    /** Restores the newest snapshot into the empty database. */
    suspend fun restoreLatest(): BackupResult = guarded {
        if (!store.isEmpty()) return@guarded BackupResult.NotEmpty
        val newest = drive.list(drive.folders().backups)
            .filter { Retention.snapshotMonth(it.name) != null }
            .maxByOrNull { Retention.snapshotMonth(it.name)!! }
            ?: return@guarded BackupResult.NothingToRestore
        val temp = tempFile()
        try {
            drive.download(newest.id, temp)
            val count = Importer(store).restore(ExportBuilder.parse(temp.readBytes()))
            BackupResult.Done("$count items from ${newest.name}")
        } finally {
            temp.delete()
        }
    }

    private suspend fun guarded(block: suspend () -> BackupResult): BackupResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DriveException.NeedsConsent) {
        BackupResult.NeedsConsent
    } catch (e: DriveException.Transient) {
        BackupResult.Offline
    } catch (e: NotEmptyException) {
        BackupResult.NotEmpty
    } catch (e: Exception) {
        BackupResult.Failed(e.message ?: "Something went wrong", e)
    }
}
