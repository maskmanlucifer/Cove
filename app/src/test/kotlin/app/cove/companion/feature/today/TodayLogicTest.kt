package app.cove.companion.feature.today

import app.cove.companion.data.local.entity.SettingsEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TodayLogicTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, 6, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun `later hides the card for half an hour`() {
        val until = snoozeUntil(at(21, 50))
        assertEquals(at(22, 20), until)
        assertTrue(cardHidden(at(22, 0), until))
        assertFalse(cardHidden(at(22, 20), until))
    }

    @Test fun `tonight ends at the next midnight`() {
        assertEquals(ZonedDateTime.of(2026, 10, 7, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), tonightEnd(at(21, 50), zone))
    }

    @Test fun `timed one-thing mode expires`() {
        val s = SettingsEntity(oneThingMode = true, oneThingUntil = at(23))
        assertTrue(oneThingActive(s, at(22)))
        assertFalse(oneThingActive(s, at(23, 1)))
        assertTrue(oneThingActive(s.copy(oneThingUntil = 0), at(23, 1)))
        assertFalse(oneThingActive(SettingsEntity(), at(8)))
    }

    @Test fun `spoken line names the wake alarm when there is one`() {
        assertEquals("Winding down. I'll keep things quiet until your 6:30am alarm.", windDownLine("6:30am"))
        assertEquals("Winding down. I'll keep things quiet tonight.", windDownLine(null))
    }
}
