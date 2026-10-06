package app.cove.companion.feature.me

import app.cove.companion.design.components.Zoom
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class ProfileRulesTest {
    @Test fun initialIsUpperCaseLetter() {
        assertEquals("M", avatarInitial("maya"))
        assertEquals("A", avatarInitial("  aman kumar"))
        assertEquals("É", avatarInitial("élodie"))
        assertEquals("7", avatarInitial("7 of 9"))
    }

    @Test fun noInitialForBlankOrSymbol() {
        assertNull(avatarInitial(""))
        assertNull(avatarInitial("   "))
        assertNull(avatarInitial("😀 Sam"))
        assertNull(avatarInitial("-x"))
    }

    @Test fun nameIsCappedAtFortyWithoutSplittingEmoji() {
        assertEquals(40, capName("a".repeat(60)).length)
        val emoji = "😀".repeat(45)
        assertEquals(40, capName(emoji).codePointCount(0, capName(emoji).length))
    }

    @Test fun normalizeCollapsesAndTrims() {
        assertEquals("Maya Rao", normalizeName("  Maya \n  Rao  "))
        assertEquals("", normalizeName("   "))
        assertEquals(40, normalizeName("b".repeat(80)).length)
    }

    @Test fun cropAtRestIsCentredSquare() {
        assertEquals(CropRect(500, 0, 1000), cropRect(2000, 1000, 300f, Zoom()))
        assertEquals(CropRect(0, 500, 1000), cropRect(1000, 2000, 300f, Zoom()))
    }

    @Test fun cropZoomShrinksTheRegion() {
        val r = cropRect(1000, 1000, 300f, Zoom(2f))
        assertEquals(500, r.size)
        assertEquals(250, r.x)
    }

    @Test fun cropDragMovesTheRegionAndStaysInside() {
        // cover scale 0.3; dragging the picture right by 30 px shows 100 source px further left
        assertEquals(CropRect(400, 0, 1000), cropRect(2000, 1000, 300f, Zoom(1f, 30f, 0f)))
        assertEquals(CropRect(0, 0, 1000), cropRect(2000, 1000, 300f, Zoom(1f, 9999f, 0f)))
    }
}
