package app.cove.companion.data.backup

import app.cove.companion.data.repo.ChangeLog
import app.cove.companion.data.sync.RoomSyncStore
import app.cove.companion.data.sync.SyncTable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Database side of backup and restore: Room in the app, a fake in tests. */
interface BackupStore {
    /** Every row of [table] as server-shaped JSON. */
    suspend fun readAll(table: SyncTable): List<JsonObject>

    /** True when nothing but the default settings row exists, the only state a restore may run in. */
    suspend fun isEmpty(): Boolean

    /** Writes [rows] into [table] and queues them for sync so a fresh account receives them. */
    suspend fun restore(table: SyncTable, rows: List<JsonObject>)
}

/** [BackupStore] over Room. */
class RoomBackupStore(private val store: RoomSyncStore, private val log: ChangeLog, private val tables: List<SyncTable>) : BackupStore {
    override suspend fun readAll(table: SyncTable) = store.readAll(table)

    override suspend fun isEmpty() = tables.filter { it.name != "settings" }.all { store.count(it) == 0 }

    override suspend fun restore(table: SyncTable, rows: List<JsonObject>) {
        store.apply(table, rows)
        rows.forEach { row -> (row[table.key] as? JsonPrimitive)?.let { log.mark(table.name, it.content) } }
    }
}
