package app.cove.companion.data

import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.sync.ConflictDescriber
import app.cove.companion.data.sync.SyncAuthException
import app.cove.companion.data.sync.SyncEngine
import app.cove.companion.data.sync.SyncRemote
import app.cove.companion.data.sync.SyncStatus
import app.cove.companion.data.sync.SyncStore
import app.cove.companion.data.sync.SyncTable
import app.cove.companion.data.sync.SyncTables
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeStore : SyncStore {
    val rows = HashMap<String, HashMap<String, JsonObject>>()
    val outbox = ArrayList<OutboxEntity>()
    val cursors = HashMap<String, Long>()
    val saved = HashMap<Pair<String, String>, SyncConflictEntity>()
    private var seq = 0L

    fun write(table: String, row: JsonObject, key: String = "id") {
        rows.getOrPut(table) { HashMap() }[(row.getValue(key) as JsonPrimitive).content] = row
        outbox += OutboxEntity(++seq, table, (row.getValue(key) as JsonPrimitive).content, "upsert", 0)
    }

    override suspend fun pendingAfter(after: Long, limit: Int) = outbox.filter { it.seq > after }.take(limit)
    override suspend fun clear(seqs: List<Long>) { outbox.removeAll { it.seq in seqs } }
    override suspend fun pendingKeys() = outbox.map { it.tableName to it.rowId }.toSet()
    override suspend fun read(table: SyncTable, ids: Collection<String>) =
        ids.mapNotNull { id -> rows[table.name]?.get(id)?.let { id to it } }.toMap()
    override suspend fun apply(table: SyncTable, rows: List<JsonObject>) {
        rows.forEach { this.rows.getOrPut(table.name) { HashMap() }[(it.getValue(table.key) as JsonPrimitive).content] = it }
    }
    override suspend fun cursor(table: String) = cursors[table] ?: 0
    override suspend fun setCursor(table: String, value: Long) { cursors[table] = value }
    override suspend fun conflicts() = saved.values.toList()
    override suspend fun saveConflict(conflict: SyncConflictEntity) { saved[conflict.tableName to conflict.rowId] = conflict }
}

private class FakeRemote : SyncRemote {
    val server = HashMap<String, HashMap<String, JsonObject>>()
    val upserts = ArrayList<Pair<String, List<JsonObject>>>()
    var failures = 0
    var auth = false
    var fetchSince = ArrayList<Long>()

    override suspend fun upsert(table: SyncTable, rows: List<JsonObject>) {
        if (auth) throw SyncAuthException()
        if (failures > 0) { failures--; throw java.io.IOException("boom") }
        upserts += table.name to rows
        rows.forEach { server.getOrPut(table.name) { HashMap() }[(it.getValue(table.key) as JsonPrimitive).content] = it }
    }

    override suspend fun fetch(table: SyncTable, since: Long, limit: Int): List<JsonObject> {
        fetchSince += since
        return server[table.name].orEmpty().values
            .filter { (it["updated_at"] as JsonPrimitive).content.toLong() >= since }
            .sortedBy { (it["updated_at"] as JsonPrimitive).content.toLong() }.take(limit)
    }
}

private fun todo(id: String, title: String, at: Long, due: Long? = null, device: String? = null) = buildJsonObject {
    put("id", id); put("title", title); put("updated_at", at)
    put("due_at", due); put("done", false)
    if (device != null) { put("device_id", device); put("device_name", "Tablet") }
}

class SyncEngineTest {
    private val store = FakeStore()
    private val remote = FakeRemote()
    private fun engine(batch: Int = 100, page: Int = 200) = SyncEngine(
        store, remote, "me-device", "Pixel", { 1_000 }, SyncTables.all, page, batch, attempts = 3, backoffMs = 1, sleep = {},
    )

    @Test fun drainsOutboxInBatchesAndClearsIt() = runBlocking {
        repeat(5) { store.write("todos", todo("t$it", "x$it", 1)) }
        assertTrue(engine(batch = 2).sync())
        assertTrue(store.outbox.isEmpty())
        assertEquals(5, remote.server["todos"]!!.size)
        assertTrue(remote.upserts.all { it.second.all { r -> r["device_id"] == JsonPrimitive("me-device") } })
    }

    @Test fun repeatedEditsOfOneRowSendOneRow() = runBlocking {
        store.write("todos", todo("a", "v1", 1)); store.write("todos", todo("a", "v2", 2))
        engine().sync()
        assertEquals(1, remote.upserts.sumOf { it.second.size })
        assertEquals(JsonPrimitive("v2"), remote.server["todos"]!!["a"]!!["title"])
    }

    @Test fun retriesWithBackoffThenSucceeds() = runBlocking {
        store.write("todos", todo("a", "v", 1)); remote.failures = 2
        assertTrue(engine().sync())
        assertTrue(store.outbox.isEmpty())
    }

    @Test fun keepsOutboxAndReportsFailureWhenRemoteStaysDown() = runBlocking {
        store.write("todos", todo("a", "v", 1)); remote.failures = 10
        val e = engine()
        assertFalse(e.sync())
        assertEquals(1, store.outbox.size)
        assertTrue(e.status.value is SyncStatus.Failed)
    }

    @Test fun authFailureIsRethrown() = runBlocking {
        store.write("todos", todo("a", "v", 1)); remote.auth = true
        try { engine().sync(); fail() } catch (_: SyncAuthException) { }
        assertEquals(1, store.outbox.size)
    }

    @Test fun pullAppliesServerRowsAndAdvancesCursor() = runBlocking {
        remote.server["todos"] = hashMapOf("s" to todo("s", "from server", 50, device = "other"))
        engine().sync()
        assertEquals(JsonPrimitive("from server"), store.rows["todos"]!!["s"]!!["title"])
        assertEquals(50L, store.cursors["todos"])
        engine().sync()
        assertTrue(remote.fetchSince.contains(50L))
    }

    @Test fun pullPagesThroughLargeResults() = runBlocking {
        remote.server["todos"] = HashMap((1..5).associate { "r$it" to todo("r$it", "t", it * 10L, device = "o") })
        engine(page = 2).sync()
        assertEquals(5, store.rows["todos"]!!.size)
        assertEquals(50L, store.cursors["todos"])
    }

    @Test fun softDeletesFromServerAreApplied() = runBlocking {
        remote.server["todos"] = hashMapOf("d" to JsonObject(todo("d", "gone", 5, device = "o") + ("deleted_at" to JsonPrimitive(5))))
        engine().sync()
        assertEquals(JsonPrimitive(5), store.rows["todos"]!!["d"]!!["deleted_at"])
    }

    @Test fun localChangeWinsInTablesWithoutConflicts() = runBlocking {
        store.write("todo_categories", buildJsonObject { put("id", "c"); put("name", "mine"); put("updated_at", 1) })
        remote.server["todo_categories"] = hashMapOf("c" to buildJsonObject { put("id", "c"); put("name", "theirs"); put("updated_at", 99); put("device_id", "o") })
        engine().sync()
        assertEquals(JsonPrimitive("mine"), remote.server["todo_categories"]!!["c"]!!["name"])
    }

    @Test fun serverRowWinsWhenNothingIsPending() = runBlocking {
        store.rows["todos"] = hashMapOf("a" to todo("a", "old", 1))
        remote.server["todos"] = hashMapOf("a" to todo("a", "new", 70, device = "o"))
        engine().sync()
        assertEquals(JsonPrimitive("new"), store.rows["todos"]!!["a"]!!["title"])
    }

    @Test fun pendingLocalPlusNewRemoteChangeCreatesConflictAndHoldsUpload() = runBlocking {
        store.cursors["todos"] = 10
        store.write("todos", todo("a", "Call mum", 20, due = 18))
        remote.server["todos"] = hashMapOf("a" to todo("a", "Call mum", 30, due = 19, device = "tablet"))
        engine().sync()
        val c = store.saved["todos" to "a"]!!
        assertEquals("Tablet", c.remoteDevice)
        assertEquals(1, store.outbox.size)
        assertTrue(remote.upserts.isEmpty())
        assertEquals(JsonPrimitive(18), store.rows["todos"]!!["a"]!!["due_at"])
        val summary = ConflictDescriber.describe(c)
        assertEquals("Call mum", summary.title)
        assertEquals("“Call mum” was changed on your phone and tablet.", summary.sentence)
    }

    @Test fun noConflictForOwnEchoIdenticalOrOldChanges() = runBlocking {
        store.cursors["todos"] = 10
        store.write("todos", todo("own", "x", 20, due = 1))
        store.write("todos", todo("same", "x", 20, due = 1))
        store.write("todos", todo("old", "x", 20, due = 1))
        remote.server["todos"] = hashMapOf(
            "own" to todo("own", "x", 30, due = 2, device = "me-device"),
            "same" to todo("same", "x", 30, due = 1, device = "tablet"),
            "old" to todo("old", "x", 10, due = 2, device = "tablet"),
        )
        engine().sync()
        assertTrue(store.saved.isEmpty())
    }
}
