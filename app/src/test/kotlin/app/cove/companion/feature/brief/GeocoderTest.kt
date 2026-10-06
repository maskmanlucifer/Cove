package app.cove.companion.feature.brief

import app.cove.companion.feature.me.calendarRowValue
import app.cove.companion.feature.me.cityLookupMessage
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class GeocoderTest {
    @Test fun parsesBestHit() {
        val p = GeocodeParser.parse("""{"results":[{"name":"Pune","latitude":18.5196,"longitude":73.8553,"country":"India"}]}""")
        assertEquals(Place("Pune", 18.5196, 73.8553), p)
    }

    @Test fun noResultsIsNull() = assertNull(GeocodeParser.parse("""{"generationtime_ms":0.3}"""))
    @Test fun garbageIsNull() = assertNull(GeocodeParser.parse("nope"))
    @Test fun messages() {
        assertEquals("Weather for Pune.", cityLookupMessage("Pune", true))
        assertEquals("Allowed", calendarRowValue(true))
    }
}
