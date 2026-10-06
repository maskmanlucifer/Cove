package app.cove.companion.data.backup

import app.cove.companion.data.sync.SyncTable
import app.cove.companion.data.sync.SyncTables

/** Thrown when a restore is attempted into a database that already holds data. */
class NotEmptyException : Exception("Cove already has data")

/** Restores a [Backup] into an empty database. */
class Importer(private val store: BackupStore, private val tables: List<SyncTable> = SyncTables.all) {
    /** Writes every table of [backup] that this build knows; unknown tables are ignored. Returns the number of rows restored. */
    suspend fun restore(backup: Backup): Int {
        if (!store.isEmpty()) throw NotEmptyException()
        var count = 0
        for (table in tables) {
            val rows = backup.tables[table.name].orEmpty()
            if (rows.isEmpty()) continue
            store.restore(table, rows)
            count += rows.size
        }
        return count
    }
}
