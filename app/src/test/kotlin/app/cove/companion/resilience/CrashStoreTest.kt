package app.cove.companion.resilience

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = CrashStore(File(tmp.root, "no_backup/crash-notes.txt"))
    private fun note(at: Long, fatal: Boolean = true) =
        CrashNote(at, "main", "java.lang.IllegalStateException", listOf("a.B.c(B.kt:1)"), "0.1.0", "uncaught", fatal)

    @Test fun roundTripsAndKeepsLastFive() {
        val s = store()
        (1..7L).forEach { s.add(note(it * 1000)) }
        assertEquals(5, s.all().size)
        assertEquals(7000L, s.last()!!.timeMs)
        assertEquals(listOf("a.B.c(B.kt:1)"), s.last()!!.frames)
        s.clear()
        assertNull(s.last())
    }

    @Test fun noteNeverContainsTheExceptionMessage() {
        val secret = "user wrote: my bank password"
        val n = CrashNote.of(IllegalStateException(secret, RuntimeException("inner $secret")), "x", "main", "1", 5, true)
        assertFalse(n.encode().contains("password"))
        assertFalse(n.toReadableText().contains("password"))
        assertEquals("java.lang.IllegalStateException", n.exception)
        assertTrue(n.frames.any { it.startsWith("root cause java.lang.RuntimeException") })
    }

    @Test fun twoFatalCrashesWithinAMinuteAreACrashLoop() {
        val now = 1_000_000L
        assertFalse(CrashLoop.isLooping(listOf(note(now - 10_000)), now))
        assertTrue(CrashLoop.isLooping(listOf(note(now - 50_000), note(now - 10_000)), now))
    }

    @Test fun oldAndNonFatalNotesDoNotCount() {
        val now = 1_000_000L
        assertFalse(CrashLoop.isLooping(listOf(note(now - 120_000), note(now - 10_000)), now))
        assertFalse(CrashLoop.isLooping(listOf(note(now - 20_000, fatal = false), note(now - 10_000)), now))
    }

    @Test fun storeCountsRecentFatalAndClearResetsTheLoop() {
        val s = store()
        s.add(note(990_000)); s.add(note(995_000))
        assertEquals(2, s.recentFatal(1_000_000))
        s.clear()
        assertEquals(0, s.recentFatal(1_000_000))
    }

    @Test fun unreadableFileIsEmptyNotACrash() {
        val f = File(tmp.root, "n.txt").apply { writeText("garbage\n---\nnot=a note") }
        assertTrue(CrashStore(f).all().isEmpty())
    }
}
