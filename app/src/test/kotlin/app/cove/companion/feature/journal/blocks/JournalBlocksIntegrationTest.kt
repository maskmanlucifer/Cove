package app.cove.companion.feature.journal.blocks

import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.insights.searchableText
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.sync.ConflictDescriber
import app.cove.companion.feature.journal.displayTitle
import java.time.YearMonth
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalBlocksIntegrationTest {
    private fun m(id: String) = JournalBodyCodec.marker(id)

    @Test fun titleNeverShowsMarkers() {
        val e = JournalEntryEntity("e", 0, "", "${m("a")}\n${m("b")}\nFirst words\n${m("c")}", null, createdAt = 0)
        assertEquals("First words", e.displayTitle())
        assertEquals("Untitled", e.copy(body = m("a")).displayTitle())
    }

    @Test fun searchTextHasNoMarkers() {
        val e = JournalEntryEntity("e", 0, "T", "hello\n${m("a")}\nworld", null, createdAt = 0)
        val text = searchableText(e, null)
        assertTrue(text.contains("hello\nworld"))
        assertFalse(text.contains("⟦"))
    }

    @Test fun markdownExportPlacesMedia() {
        val entry = buildJsonObject {
            put("id", "e1"); put("day", 20_000L); put("title", "Day"); put("created_at", 1L); put("deleted_at", JsonNull)
            put("body", "Morning\n${m("p1")}\n${m("v1")}\nEvening")
        }
        val photo = buildJsonObject { put("id", "p1"); put("entry_id", "e1"); put("kind", "photo"); put("local_path", "/x/p1.webp"); put("deleted_at", JsonNull) }
        val voice = buildJsonObject { put("id", "v1"); put("entry_id", "e1"); put("kind", "voice"); put("local_path", "/x/v1.ogg"); put("duration_ms", 42_000L); put("deleted_at", JsonNull) }
        val doc = ExportBuilder.journalMarkdown(listOf(entry), listOf(photo, voice)).getValue(YearMonth.of(2024, 10))
        assertTrue(doc, doc.contains("Morning\n\n![photo](../Photos/p1.webp)\n\n🎙 voice note (0:42)\n\nEvening"))
        assertFalse(doc.contains("⟦"))
    }

    @Test fun conflictShowsPlainText() {
        fun row(body: String) = buildJsonObject { put("id", "e"); put("title", "Day"); put("body", body) }.toString()
        val c = SyncConflictEntity("journal_entries", "e", row("Mine\n${m("a")}"), row("Theirs\n${m("b")}"), "Tablet", 1, 1, 1)
        val s = ConflictDescriber.describe(c)
        assertEquals("Mine", s.local.value)
        assertEquals("Theirs", s.remote.value)
    }
}
