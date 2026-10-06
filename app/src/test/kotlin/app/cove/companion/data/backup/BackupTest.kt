package app.cove.companion.data.backup

import app.cove.companion.data.drive.DriveFile
import app.cove.companion.data.drive.FakeDriveClient
import app.cove.companion.data.sync.SyncTable
import app.cove.companion.data.sync.SyncTables
import java.io.File
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private class MemStore(val data: MutableMap<String, List<JsonObject>> = HashMap()) : BackupStore {
        override suspend fun readAll(table: SyncTable) = data[table.name].orEmpty()
        override suspend fun isEmpty() = data.filterKeys { it != "settings" }.values.all { it.isEmpty() }
        override suspend fun restore(table: SyncTable, rows: List<JsonObject>) { data[table.name] = rows }
    }

    private fun entry(id: String, day: Long, title: String, deleted: Boolean = false) = buildJsonObject {
        put("id", id); put("day", day); put("title", title); put("body", "Body of $id"); put("mood", "calm")
        put("created_at", day * 1000); put("updated_at", 5L)
        put("deleted_at", if (deleted) JsonPrimitive(9L) else kotlinx.serialization.json.JsonNull)
    }

    private fun seeded() = MemStore(
        mutableMapOf(
            "settings" to listOf(buildJsonObject { put("id", "me"); put("display_name", "Maya"); put("onboarded", true) }),
            "todos" to listOf(buildJsonObject { put("id", "b"); put("title", "B"); put("done", false) }, buildJsonObject { put("id", "a"); put("title", "A"); put("done", true) }),
            "journal_entries" to listOf(entry("e1", 20_000, "One"), entry("e2", 20_040, "Two"), entry("gone", 20_001, "Deleted", deleted = true)),
            "suggestion_prefs" to listOf(buildJsonObject { put("kind", "walk"); put("muted", true) }),
            "briefs" to listOf(buildJsonObject { put("day", 20_000L); put("segments", "[]") }),
        ),
    )

    private fun export(store: MemStore): String = runBlocking {
        ExportBuilder.snapshotJson(42, SyncTables.all.associate { it.name to store.readAll(it) }) { SyncTables.find(it)?.key ?: "id" }
    }

    @Test fun exportRoundTripsIntoEmptyStore() = runBlocking {
        val source = seeded()
        val parsed = ExportBuilder.parse(ExportBuilder.gzip(export(source)))
        assertEquals(1, parsed.version)
        assertEquals(42L, parsed.createdAt)
        val target = MemStore(mutableMapOf("settings" to listOf(buildJsonObject { put("id", "me") })))
        val count = Importer(target).restore(parsed)
        assertEquals(8, count)
        for (t in SyncTables.all) {
            val key = t.key
            assertEquals(t.name, source.data[t.name].orEmpty().sortedBy { it[key].toString() }, target.data[t.name].orEmpty().sortedBy { it[key].toString() })
        }
    }

    @Test fun schemaIsStableAndExcludesLocalTables() {
        val json = export(seeded())
        assertTrue(json.startsWith("""{"version":1,"app":"cove","createdAt":42,"tables":{"settings":"""))
        assertEquals(json, export(seeded()))
        assertTrue(json.indexOf("\"id\":\"a\"") < json.indexOf("\"id\":\"b\""))
        for (local in listOf("search_index", "outbox", "sync_state", "sync_conflicts")) assertFalse(json.contains("\"$local\""))
    }

    @Test fun rejectsNewerVersionGarbageAndNonEmptyTarget() = runBlocking {
        try { ExportBuilder.parse("""{"version":99,"tables":{}}""".toByteArray()); fail() } catch (e: BackupFormatException) { }
        try { ExportBuilder.parse("nope".toByteArray()); fail() } catch (e: BackupFormatException) { }
        try { Importer(seeded()).restore(Backup(1, 0, emptyMap())); fail() } catch (e: NotEmptyException) { }
    }

    @Test fun journalMarkdownGroupsByMonthAndSkipsDeleted() {
        val md = ExportBuilder.journalMarkdown(seeded().data.getValue("journal_entries"))
        assertEquals(setOf(YearMonth.of(2024, 10), YearMonth.of(2024, 11)), md.keys)
        val oct = md.getValue(YearMonth.of(2024, 10))
        assertTrue(oct.startsWith("# Journal, October 2024\n\n## 2024-10-04 · One\n\nMood: calm\n\nBody of e1\n"))
        assertFalse(oct.contains("Deleted"))
        assertEquals("journal-2024-10.md", ExportBuilder.journalName(YearMonth.of(2024, 10)))
        assertEquals("cove-2024-10.json.gz", ExportBuilder.snapshotName(YearMonth.of(2024, 10)))
    }

    @Test fun retentionKeepsNewestTwelveSnapshotsAndDedupes() {
        val snaps = (1..14).map { m -> DriveFile("s$m", "cove-${2025 + (m - 1) / 12}-${((m - 1) % 12 + 1).toString().padStart(2, '0')}.json.gz", m * 10L) }
        val md = DriveFile("md", "journal-2025-01.md", 1)
        val dup = DriveFile("dup", snaps.last().name, 1)
        val doomed = Retention.toDelete(snaps + md + dup).map { it.id }.toSet()
        assertEquals(setOf("s1", "s2", "dup"), doomed)
        assertEquals(YearMonth.of(2025, 3), Retention.snapshotMonth("cove-2025-03.json.gz"))
        assertEquals(null, Retention.snapshotMonth("journal-2025-03.md"))
    }

    @Test fun serviceBacksUpToDriveAndRestoresNewest() = runBlocking {
        val drive = FakeDriveClient(File(tmp.root, "drive"))
        val july = java.time.LocalDate.of(2026, 7, 15).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val source = seeded()
        val result = BackupService(source, drive, { july }, ZoneOffset.UTC, { File(tmp.root, "t.gz") }).backUp()
        assertEquals(BackupResult.Done("cove-2026-07.json.gz"), result)
        val names = drive.list(drive.folders().backups).map { it.name }.toSet()
        assertEquals(setOf("cove-2026-07.json.gz", "journal-2024-10.md", "journal-2024-11.md"), names)

        val fresh = MemStore()
        val restored = BackupService(fresh, drive, { july }, ZoneOffset.UTC, { File(tmp.root, "t.gz") }).restoreLatest()
        assertTrue(restored is BackupResult.Done)
        assertEquals(source.data["todos"]!!.size, fresh.data["todos"]!!.size)
        assertEquals(BackupResult.NotEmpty, BackupService(source, drive, { july }, ZoneOffset.UTC, { File(tmp.root, "t.gz") }).restoreLatest())
    }
}
