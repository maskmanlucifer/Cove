package app.cove.companion.data.backup

import app.cove.companion.data.drive.DriveFile
import java.time.YearMonth

/** Which Drive backup files to delete so only the newest snapshots remain. */
object Retention {
    /** Snapshots kept. */
    const val KEEP = 12

    private val snapshot = Regex("""cove-(\d{4}-\d{2})\.json\.gz""")

    /** The month of a snapshot [name], or null for other files. */
    fun snapshotMonth(name: String): YearMonth? =
        snapshot.matchEntire(name)?.let { runCatching { YearMonth.parse(it.groupValues[1]) }.getOrNull() }

    /**
     * Files to delete: older duplicates of any name (a month re-backed up), and snapshots beyond the newest [keep]
     * months. Journal Markdown files are small, readable archives and are kept.
     */
    fun toDelete(files: List<DriveFile>, keep: Int = KEEP): List<DriveFile> {
        val duplicates = files.groupBy { it.name }.values.flatMap { group -> group.sortedByDescending { it.createdTime }.drop(1) }
        val survivors = files - duplicates.toSet()
        val old = survivors.filter { snapshotMonth(it.name) != null }
            .sortedByDescending { snapshotMonth(it.name) }
            .drop(keep)
        return duplicates + old
    }
}
