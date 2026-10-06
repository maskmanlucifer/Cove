package app.cove.companion.data.wipe

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WipeProcessorTest {
    private lateinit var root: File
    private lateinit var dirs: WipeDirs
    private var keysErased = 0
    private var keysFail = false

    @Before fun setUp() {
        root = Files.createTempDirectory("wipe").toFile()
        dirs = WipeDirs(
            databases = File(root, "databases"), files = File(root, "files"), noBackup = File(root, "no_backup"),
            cache = File(root, "cache"), prefs = File(root, "shared_prefs"), root = root,
        )
        listOf("databases/cove.db", "databases/cove.db-wal", "databases/androidx.work.workdb", "files/journal/p.webp", "files/datastore/brief.preferences_pb",
            "no_backup/cove.key", "no_backup/cove-credentials.bin", "no_backup/crash-notes.txt", "no_backup/androidx.work.workdb", "cache/restore.json.gz",
            "shared_prefs/cove_session.xml", "shared_prefs/cove_drive.xml", "shared_prefs/androidx.work.util.preferences.xml",
        ).forEach { File(root, it).apply { parentFile!!.mkdirs() }.writeText("x") }
    }

    private fun processor(retries: Int = 1) = WipeProcessor(dirs, { if (keysFail) error("keystore") else keysErased++ }, retries = retries, pause = {})

    private fun request(notice: String = PendingWipe.DEFAULT_NOTICE) = PendingWipe.write(dirs.marker, PendingWipe(1L, notice))

    @Test fun withoutMarkerNothingIsTouched() {
        assertEquals(WipeOutcome.NothingPending, processor().processIfPending())
        assertTrue(File(root, "files/journal/p.webp").exists())
    }

    @Test fun fullWipeRemovesEverythingIncludingConnections() {
        request()
        assertEquals(WipeOutcome.Done, processor().processIfPending())
        assertFalse(File(root, "databases/cove.db").exists())
        assertFalse(File(root, "databases/cove.db-wal").exists())
        assertFalse(File(root, "files/journal").exists())
        assertFalse(File(root, "no_backup/cove-credentials.bin").exists())
        assertFalse(File(root, "no_backup/cove.key").exists())
        assertFalse(File(root, "shared_prefs/cove_session.xml").exists())
        assertFalse(File(root, "shared_prefs/cove_drive.xml").exists())
        assertEquals(1, keysErased)
        assertFalse(dirs.marker.exists())
    }

    @Test fun libraryBookkeepingSurvives() {
        request()
        processor().processIfPending()
        assertTrue(File(root, "databases/androidx.work.workdb").exists())
        assertTrue(File(root, "shared_prefs/androidx.work.util.preferences.xml").exists())
        assertTrue(File(root, "no_backup/androidx.work.workdb").exists())
    }

    @Test fun noticeIsWrittenForTheNextLaunch() {
        request("Your data on this phone was cleared.\nSupabase: 3 rows.")
        processor().processIfPending()
        assertEquals("Your data on this phone was cleared.\nSupabase: 3 rows.", dirs.notice.readText())
    }

    @Test fun interruptedWipeIsFinishedByTheNextStart() {
        request()
        keysFail = true
        val first = processor().processIfPending()
        assertTrue(first is WipeOutcome.Partial)
        assertTrue(dirs.marker.exists())
        assertFalse(File(root, "databases/cove.db").exists())
        keysFail = false
        assertEquals(WipeOutcome.Done, processor().processIfPending())
        assertFalse(dirs.marker.exists())
        assertEquals(WipeOutcome.NothingPending, processor().processIfPending())
    }

    @Test fun unreadableMarkerStillWipesWithDefaultNotice() {
        dirs.marker.writeText("{broken")
        assertEquals(WipeOutcome.Done, processor().processIfPending())
        assertEquals(PendingWipe.DEFAULT_NOTICE, dirs.notice.readText())
    }

    @Test fun missingFoldersAreFine() {
        root.resolve("cache").deleteRecursively()
        root.resolve("shared_prefs").deleteRecursively()
        request()
        assertEquals(WipeOutcome.Done, processor().processIfPending())
    }

    @Test fun markerRoundTrips() {
        request("hello")
        assertEquals(PendingWipe(1L, "hello"), PendingWipe.read(dirs.marker))
    }
}
