package app.cove.companion.data.sync

import app.cove.companion.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Pull-then-push sync between a [SyncStore] and a [SyncRemote].
 *
 * Pull first so a stale local edit can be detected before it overwrites the server. For each table the engine reads
 * rows with `updated_at >= cursor`. A row with no pending local change is applied (server `updated_at` wins:
 * last writer wins). A row with a pending change keeps the local version, except in conflict-aware tables
 * (alarms, events, todos) where a changed-elsewhere row becomes a [SyncConflictEntity] and is held back from upload
 * until the user decides.
 */
class SyncEngine(
    private val store: SyncStore,
    private val remote: SyncRemote,
    private val deviceId: String,
    private val deviceName: String,
    private val now: () -> Long,
    private val tables: List<SyncTable> = SyncTables.all,
    private val pageSize: Int = 200,
    private val batchSize: Int = 100,
    private val attempts: Int = 3,
    private val backoffMs: Long = 500,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Publishes the last successful sync time that was saved on a previous run. */
    fun restore(lastSyncAt: Long) {
        if (lastSyncAt > 0 && _status.value == SyncStatus.Idle) _status.value = SyncStatus.UpToDate(lastSyncAt)
    }

    /**
     * Runs one full pull and push.
     *
     * @return true on success; false when it should be retried later. [SyncAuthException] is rethrown so the
     * caller can refresh the session.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        _status.value = SyncStatus.Syncing
        try {
            pull()
            push()
            _status.value = SyncStatus.UpToDate(now())
            true
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle
            throw e
        } catch (e: SyncAuthException) {
            _status.value = SyncStatus.Failed("Signed out")
            throw e
        } catch (e: Exception) {
            _status.value = SyncStatus.Failed(e.message ?: "Sync failed")
            false
        }
    }

    private suspend fun pull() {
        val pending = store.pendingKeys()
        for (table in tables) {
            var cursor = store.cursor(table.name)
            while (true) {
                val rows = retrying { remote.fetch(table, cursor, pageSize) }
                if (rows.isEmpty()) break
                applyPage(table, rows, cursor, pending)
                val next = rows.maxOf { RowJson.long(it, "updated_at") }
                store.setCursor(table.name, maxOf(next, cursor))
                if (rows.size < pageSize || next <= cursor) break
                cursor = next
            }
        }
    }

    private suspend fun applyPage(table: SyncTable, rows: List<JsonObject>, cursor: Long, pending: Set<Pair<String, String>>) {
        val held = rows.filter { (table.name to RowJson.string(it, table.key)) in pending }
        val local = if (held.isEmpty()) emptyMap() else store.read(table, held.mapNotNull { RowJson.string(it, table.key) })
        val heldIds = held.mapNotNull { RowJson.string(it, table.key) }.toSet()
        store.apply(table, rows.filter { RowJson.string(it, table.key) !in heldIds })
        for (remoteRow in held) {
            val id = RowJson.string(remoteRow, table.key) ?: continue
            val mine = local[id] ?: continue
            if (isConflict(table, mine, remoteRow, cursor)) {
                store.saveConflict(
                    SyncConflictEntity(
                        tableName = table.name,
                        rowId = id,
                        localJson = mine.toString(),
                        remoteJson = remoteRow.toString(),
                        remoteDevice = RowJson.string(remoteRow, "device_name") ?: "Another device",
                        localAt = RowJson.long(mine, "updated_at"),
                        remoteAt = RowJson.long(remoteRow, "updated_at"),
                        detectedAt = now(),
                    ),
                )
            }
        }
    }

    /** A pending local row only conflicts with a genuinely new change made on another device. */
    private fun isConflict(table: SyncTable, local: JsonObject, remote: JsonObject, cursor: Long): Boolean =
        table.conflictAware &&
            RowJson.long(remote, "updated_at") > cursor &&
            RowJson.string(remote, "device_id") != deviceId &&
            !RowJson.sameData(local, remote)

    private suspend fun push() {
        val conflicts = store.conflicts().map { it.tableName to it.rowId }.toSet()
        var after = 0L
        while (true) {
            val batch = store.pendingAfter(after, batchSize)
            if (batch.isEmpty()) break
            after = batch.last().seq
            val sendable = batch.filter { (it.tableName to it.rowId) !in conflicts }
            for (table in tables) {
                val entries = sendable.filter { it.tableName == table.name }
                if (entries.isEmpty()) continue
                val rows = store.read(table, entries.map { it.rowId }.distinct())
                if (rows.isNotEmpty()) {
                    val stamped = rows.values.map { RowJson.stamped(it, deviceId, deviceName) }
                    retrying { remote.upsert(table, stamped) }
                }
                store.clear(entries.map { it.seq })
            }
        }
    }

    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = backoffMs
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncAuthException) {
                throw e
            } catch (e: Exception) {
                sleep(wait)
                wait *= 2
            }
        }
        return block()
    }
}
