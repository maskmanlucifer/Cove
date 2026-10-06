package app.cove.companion.design

import androidx.compose.ui.geometry.Size
import app.cove.companion.design.components.Zoom
import app.cove.companion.design.components.clampZoom
import app.cove.companion.design.components.coverSize
import app.cove.companion.design.components.doubleTapZoom
import app.cove.companion.design.components.fitSize
import app.cove.companion.design.components.zoomBy
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ZoomTest {
    private val view = Size(400f, 800f)
    private val content = fitSize(2000f, 1000f, 400f, 800f)

    @Test fun fitAndCover() {
        assertEquals(Size(400f, 200f), content)
        assertEquals(Size(1600f, 800f), coverSize(2000f, 1000f, 400f, 800f))
    }

    @Test fun cannotPanAtRest() {
        assertEquals(Zoom(), clampZoom(Zoom(1f, 50f, 50f), view, content))
    }

    @Test fun scaleIsClamped() {
        assertEquals(4f, zoomBy(Zoom(), 10f, 0f, 0f, 0f, 0f, view, content).scale, 0f)
        assertEquals(1f, zoomBy(Zoom(2f), 0.1f, 0f, 0f, 0f, 0f, view, content).scale, 0f)
    }

    @Test fun panStopsAtPictureEdge() {
        val z = zoomBy(Zoom(), 4f, 5000f, 0f, 0f, 0f, view, content)
        assertEquals(600f, z.x, 0.001f)
        assertEquals(0f, z.y, 0.001f) // 200 * 4 = 800 fits the view height exactly
    }

    @Test fun pinchKeepsPointUnderFingers() {
        val z = zoomBy(Zoom(), 2f, 0f, 0f, 100f, 0f, view, content)
        // the picture point at x=100 stays at x=100: offset = 100 - 2 * 100
        assertEquals(-100f, z.x, 0.001f)
    }

    @Test fun doubleTapTogglesBetweenFitAndZoom() {
        val zoomed = doubleTapZoom(Zoom(), 0f, 0f, view, content)
        assertTrue(zoomed.zoomed)
        assertEquals(2.5f, zoomed.scale, 0f)
        assertFalse(doubleTapZoom(zoomed, 0f, 0f, view, content).zoomed)
    }
}
