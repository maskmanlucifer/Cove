package app.cove.companion.data.memory

import app.cove.companion.data.local.entity.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryLogicTest {
    private fun mem(id: String, subject: String, text: String, at: Long, detail: String = "") =
        MemoryEntity(id, text, subject, detail, "place", MemoryText.keywords("$subject $text").joinToString(" "), at)

    @Test fun stemmingJoinsWordForms() {
        assertEquals(MemoryText.keywords("parked"), MemoryText.keywords("parking"))
        assertEquals(listOf("key"), MemoryText.keywords("my keys"))
    }

    @Test fun theSubjectOutranksAMentionInAnotherNote() {
        val car = mem("1", "car", "I parked on level 3", 1_000, "on level 3")
        val other = mem("2", "dentist", "Dentist said park the car outside", 2_000)
        assertEquals("1", MemoryRanker.pick("where is my car", listOf(other, car))?.id)
    }

    @Test fun newerWinsATie() {
        val old = mem("1", "keys", "keys on the shelf", 1_000)
        val new = mem("2", "keys", "keys in the drawer", 2_000)
        assertEquals("2", MemoryRanker.pick("where are my keys", listOf(new, old))?.id)
    }

    @Test fun nothingMatchesNothing() {
        assertNull(MemoryRanker.pick("where is my passport", listOf(mem("1", "car", "parked here", 1_000))))
        assertNull(MemoryRanker.pick("where", emptyList()))
    }

    @Test fun answersSayHowLongAgo() {
        val m = mem("1", "car", "I parked on level 3", 0, "on level 3")
        assertEquals("Car: on level 3. You told me 2 hours ago.", MemoryAnswer.say(m, 2 * 60 * 60_000L))
        assertEquals("an hour ago", MemoryAnswer.ago(61 * 60_000L))
        assertEquals("yesterday", MemoryAnswer.ago(25 * 60 * 60_000L))
        assertEquals("just now", MemoryAnswer.ago(5_000))
    }

    @Test fun onlyParkedVehiclesExpire() {
        assertEquals(24 * 60 * 60 * 1000L, MemoryPolicy.keepFor("Car"))
        assertNull(MemoryPolicy.keepFor("passport"))
    }
}
