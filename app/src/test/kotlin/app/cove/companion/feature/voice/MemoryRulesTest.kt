package app.cove.companion.feature.voice

import app.cove.companion.ai.model.MemoryNotes
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {
    private val parser = RuleParser(Clock { LocalDateTime.of(2026, 10, 6, 10, 35).toEpochMillis() })
    private fun one(text: String) = parser.parse(text).singleOrNull()
    private fun remember(text: String) = one(text) as VoiceIntent.Remember

    @Test fun parkingIsRememberedAsAPlaceThatExpires() {
        val m = remember("I parked on level 3, pillar B")
        assertEquals("car", m.subject)
        assertEquals("on level 3, pillar B", m.detail)
        assertEquals("place", m.kind)
        assertEquals(24 * 60 * 60 * 1000L, m.keepForMs)
    }

    @Test fun objectLocationsAreRemembered() {
        val a = remember("I put my passport in the blue folder")
        assertEquals("passport", a.subject)
        assertEquals("in the blue folder", a.detail)
        assertNull(a.keepForMs)
        val b = remember("my keys are on the shelf")
        assertEquals("keys", b.subject)
        assertEquals("on the shelf", b.detail)
    }

    @Test fun explicitRememberKeepsTheNote() {
        val m = remember("remember that the wifi password is on the router")
        assertEquals("note", m.kind)
        assertTrue(m.text.startsWith("The wifi password"))
    }

    @Test fun questionsAreRecalled() {
        assertEquals(VoiceIntent.Recall("car park"), one("where did I park"))
        assertEquals(VoiceIntent.Recall("passport"), one("where is my passport"))
        assertEquals(VoiceIntent.Recall("keys"), one("where did I put my keys?"))
        assertEquals(VoiceIntent.Recall("geyser"), one("what did I say about the geyser"))
    }

    @Test fun inAppCommandsAreNeverTakenForNotes() {
        assertTrue(one("remind me to call mom at 6") is VoiceIntent.AddReminder)
        assertTrue(one("remember to buy milk") !is VoiceIntent.Remember)
        assertTrue(one("set an alarm for 6 am") is VoiceIntent.SetAlarm)
        assertTrue(one("spent 250 on lunch") is VoiceIntent.LogExpense)
    }

    @Test fun aMeetingTimeIsNotAPlace() {
        assertFalse(one("my meeting is at 5 pm") is VoiceIntent.Remember)
    }

    @Test fun onlyStatementsLookLikeNotes() {
        assertTrue(MemoryNotes.looksLikeNote("the geyser guy said he will come on sunday"))
        assertFalse(MemoryNotes.looksLikeNote("what time is it now"))
        assertFalse(MemoryNotes.looksLikeNote("is the geyser fixed?"))
        assertFalse(MemoryNotes.looksLikeNote("hello there"))
    }
}
