package app.cove.companion.data.sync

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.local.entity.SyncStateEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** [SyncStore] over Room using generic SQL, converting camelCase columns and 0/1 booleans to server JSON. */
class RoomSyncStore(private val db: CoveDatabase) : SyncStore {
    private val dao get() = db.sync()

    override suspend fun pendingAfter(after: Long, limit: Int): List<OutboxEntity> = dao.pendingAfter(after, limit)

    override suspend fun clear(seqs: List<Long>) = seqs.chunked(500).forEach { dao.clear(it) }

    override suspend fun pendingKeys(): Set<Pair<String, String>> = dao.pendingKeys().map { it.tableName to it.rowId }.toSet()

    override suspend fun read(table: SyncTable, ids: Collection<String>): Map<String, JsonObject> {
        val out = HashMap<String, JsonObject>()
        val keyColumn = localColumns(table).entries.first { it.value == table.key }.key
        for (chunk in ids.chunked(500)) {
            val sql = "SELECT * FROM ${table.name} WHERE $keyColumn IN (${chunk.joinToString(",") { "?" }})"
            db.query(SimpleSQLiteQuery(sql, chunk.toTypedArray())).use { c ->
                while (c.moveToNext()) {
                    val row = toJson(table, c)
                    out[(row.getValue(table.key) as JsonPrimitive).content] = row
                }
            }
        }
        return out
    }

    /** Every local row of [table] as server-shaped JSON, used by backups. */
    suspend fun readAll(table: SyncTable): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        db.query(SimpleSQLiteQuery("SELECT * FROM ${table.name}")).use { c ->
            while (c.moveToNext()) out.add(toJson(table, c))
        }
        return out
    }

    /** Number of rows in [table]. */
    suspend fun count(table: SyncTable): Int =
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM ${table.name}")).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    override suspend fun apply(table: SyncTable, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        val columns = localColumns(table)
        val keyColumn = columns.entries.first { it.value == table.key }.key
        db.withTransaction {
            val sdb = db.openHelper.writableDatabase
            for (row in rows) {
                val names = columns.filterValues { it in row }.keys.toList()
                if (keyColumn !in names) continue
                val updates = names.filter { columns.getValue(it) !in table.localOnly && it != keyColumn }
                val sql = buildString {
                    append("INSERT OR REPLACE INTO ${table.name} (${names.joinToString(",")}) VALUES (${names.joinToString(",") { "?" }})")
                    if (updates.isNotEmpty()) {
                        append(" ON CONFLICT($keyColumn) DO UPDATE SET ${updates.joinToString(",") { "$it=excluded.$it" }}")
                    }
                }
                sdb.execSQL(sql, names.map { toArg(row.getValue(columns.getValue(it))) }.toTypedArray())
            }
        }
    }

    override suspend fun cursor(table: String): Long = dao.state(table)?.lastPullAt ?: 0

    override suspend fun setCursor(table: String, value: Long) = dao.setState(SyncStateEntity(table, value))

    override suspend fun conflicts(): List<SyncConflictEntity> = dao.conflicts()

    override suspend fun saveConflict(conflict: SyncConflictEntity) = dao.saveConflict(conflict)

    /** Local column name to server column name for [table]. */
    private fun localColumns(table: SyncTable): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        db.query(SimpleSQLiteQuery("PRAGMA table_info(${table.name})")).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) c.getString(nameIdx).let { out[it] = SyncTables.snake(it) }
        }
        return out
    }

    private fun toJson(table: SyncTable, c: Cursor): JsonObject {
        val map = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        for (i in 0 until c.columnCount) {
            val name = SyncTables.snake(c.getColumnName(i))
            map[name] = when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL, Cursor.FIELD_TYPE_BLOB -> JsonNull
                Cursor.FIELD_TYPE_INTEGER -> if (name in table.bools) JsonPrimitive(c.getLong(i) != 0L) else JsonPrimitive(c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                else -> JsonPrimitive(c.getString(i))
            }
        }
        return JsonObject(map)
    }

    private fun toArg(e: kotlinx.serialization.json.JsonElement): Any? {
        val p = e as? JsonPrimitive ?: return e.toString()
        if (e is JsonNull) return null
        if (p.isString) return p.content
        p.booleanOrNull?.let { return if (it) 1L else 0L }
        p.longOrNull?.let { return it }
        return p.doubleOrNull
    }
}
