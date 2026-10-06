package app.cove.companion.data.sync

import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import kotlinx.serialization.json.JsonObject

/** Local side of sync: Room in the app, a fake in tests. Rows are exchanged as server-shaped JSON. */
interface SyncStore {
    /** Outbox entries with `seq` greater than [after], oldest first. */
    suspend fun pendingAfter(after: Long, limit: Int): List<OutboxEntity>

    /** Removes the outbox entries with these sequence numbers. */
    suspend fun clear(seqs: List<Long>)

    /** `(table, rowId)` pairs that still have an unsent change. */
    suspend fun pendingKeys(): Set<Pair<String, String>>

    /** Current local rows of [table] with these ids (missing ids are absent from the result), keyed by id. */
    suspend fun read(table: SyncTable, ids: Collection<String>): Map<String, JsonObject>

    /** Writes server rows into [table] without queuing them for upload. */
    suspend fun apply(table: SyncTable, rows: List<JsonObject>)

    suspend fun cursor(table: String): Long
    suspend fun setCursor(table: String, value: Long)

    suspend fun conflicts(): List<SyncConflictEntity>
    suspend fun saveConflict(conflict: SyncConflictEntity)
}

/** Remote side of sync: Supabase PostgREST in the app, a fake in tests. */
interface SyncRemote {
    /** Upserts [rows] into [table]; throws [SyncAuthException] on 401 and another exception on other failures. */
    suspend fun upsert(table: SyncTable, rows: List<JsonObject>)

    /** Rows with `updated_at >= since`, oldest first, at most [limit]. */
    suspend fun fetch(table: SyncTable, since: Long, limit: Int): List<JsonObject>
}

/** The server rejected our credentials; the caller should refresh the session or sign out. */
class SyncAuthException : Exception("Unauthorized")
