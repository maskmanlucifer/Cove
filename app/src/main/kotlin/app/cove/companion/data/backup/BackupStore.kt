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

    /** True when there is no user-authored data (see [RestoreRule]), the only state a restore may run in. */
    suspend fun isEmpty(): Boolean

    /** Removes the alarm onboarding creates by default, so a restored wake alarm does not end up next to it. */
    suspend fun clearDefaultAlarms() {}

    /** Writes [rows] into [table] and queues them for sync so a fresh account receives them. */
    suspend fun restore(table: SyncTable, rows: List<JsonObject>)
}

/** [BackupStore] over Room. */
class RoomBackupStore(private val store: RoomSyncStore, private val log: ChangeLog, private val tables: List<SyncTable>) : BackupStore {
    override suspend fun readAll(table: SyncTable) = store.readAll(table)

    override suspend fun isEmpty(): Boolean {
        val counts = tables.associate { it.name to store.count(it) }
        val defaults = tables.firstOrNull { it.name == "alarms" }?.let { store.countWhere(it, RestoreRule.DEFAULT_ALARM_SQL) } ?: 0
        return RestoreRule.isEmpty(counts, defaults)
    }

    override suspend fun clearDefaultAlarms() {
        tables.firstOrNull { it.name == "alarms" }?.let { store.deleteWhere(it, RestoreRule.DEFAULT_ALARM_SQL) }
    }

    override suspend fun restore(table: SyncTable, rows: List<JsonObject>) {
        store.apply(table, rows)
        rows.forEach { row -> (row[table.key] as? JsonPrimitive)?.let { log.mark(table.name, it.content) } }
    }
}

/**
 * "Empty" for restore means "no user-authored data": the settings row and the wake alarm that onboarding creates
 * by default do not count, so Restore keeps working right after a fresh install was set up.
 */
object RestoreRule {
    /** SQL condition for the onboarding default alarm (`saveWakeTime` creates it with this kind and label). */
    const val DEFAULT_ALARM_SQL = "kind = 'wake' AND label = 'Wake up'"

    /** True when every table except `settings` is empty once [defaultAlarms] of the `alarms` rows are set aside. */
    fun isEmpty(counts: Map<String, Int>, defaultAlarms: Int): Boolean = counts.all { (table, n) ->
        when (table) {
            "settings" -> true
            "alarms" -> n - defaultAlarms <= 0
            else -> n == 0
        }
    }
}
