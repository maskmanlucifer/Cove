package app.cove.companion.data.wipe

import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DataPreviewTest {
    @Test fun countsAndSizesBecomeSentences() {
        Locale.setDefault(Locale.US)
        val journal = Files.createTempDirectory("j").toFile()
        File(journal, "photos").mkdirs()
        File(journal, "photos/a.webp").writeBytes(ByteArray(1_500_000))
        File(journal, "b.m4a").writeBytes(ByteArray(500_000))
        val db = File.createTempFile("cove", ".db").apply { writeBytes(ByteArray(2_000)) }
        val counts = mapOf("todos" to 1, "journal_entries" to 12, "alarms" to 0)
        val p = DataPreview.build({ counts[it] ?: 0 }, journal, listOf(db))
        assertEquals(listOf("12 journal entries", "1 to-do", "2 photos, voice notes and thumbnails (2.0 MB)"), p.sentences())
        assertEquals("2.0 KB", p.databaseSize())
    }

    @Test fun emptyPhoneStillSaysSomething() {
        val p = DataPreview.build({ 0 }, File("/nonexistent"), emptyList())
        assertEquals(listOf("No entries yet."), p.sentences())
    }

    @Test fun sizeFormatting() {
        assertEquals("999 B", DataPreview.formatSize(999))
        assertEquals("1.0 KB", DataPreview.formatSize(1_000))
        assertEquals("14.2 MB", DataPreview.formatSize(14_200_000))
    }
}
