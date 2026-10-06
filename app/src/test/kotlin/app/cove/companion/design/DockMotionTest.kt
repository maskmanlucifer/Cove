package app.cove.companion.design

import app.cove.companion.design.components.DockMotion
import app.cove.companion.design.components.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMotionTest {
    @Test
    fun activeIconNeverMovesAndBarFitsBesideTheOrb() {
        for (w in listOf(360f, 392f, 411f, 480f)) {
            for (tab in Tab.entries) assertTrue("$w ${tab.name}", DockMotion.fits(w, tab.ordinal, 52f))
        }
    }

    @Test
    fun framesStartAndEndCleanly() {
        val closed = DockMotion.frame(0f, false)
        assertEquals(0f, closed.geom, 0f); assertEquals(0f, closed.otherAlpha, 0f); assertEquals(1f, closed.labelAlpha, 0f)
        val open = DockMotion.frame(1f, false)
        assertEquals(1f, open.geom, 0f); assertEquals(1f, open.otherAlpha, 0f); assertEquals(0f, open.labelAlpha, 0f)
    }

    @Test
    fun labelAndOthersNeverBothShowMuch() {
        for (i in 0..100) {
            val f = DockMotion.frame(i / 100f, false)
            assertTrue(f.labelAlpha + f.otherAlpha <= 1.0001f)
        }
    }

    @Test
    fun reducedMotionSnapsGeometryAndCrossfades() {
        assertEquals(0f, DockMotion.frame(0.3f, true).geom, 0f)
        assertEquals(1f, DockMotion.frame(0.7f, true).geom, 0f)
        assertEquals(0f, DockMotion.frame(0.5f, true).otherAlpha, 0f)
    }

    @Test
    fun durationIsWithinTheDesignedRange() {
        assertTrue(DockMotion.DURATION_MS in 200..260)
    }
}
