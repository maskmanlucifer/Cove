package app.cove.companion.design

import app.cove.companion.design.illustrations.ArtKey
import app.cove.companion.design.illustrations.ByteLru
import app.cove.companion.design.illustrations.Fit
import app.cove.companion.design.illustrations.blinkAt
import app.cove.companion.design.illustrations.dotField
import app.cove.companion.design.illustrations.fitTransform
import app.cove.companion.design.illustrations.strokeField
import app.cove.companion.design.illustrations.swayAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationHelpersTest {
    private fun strokes(seed: Long) = strokeField(seed, 50, 10f, 20f, 90f, 60f, 3f..8f, -90f)
        .map { listOf(it.x, it.y, it.len, it.angle, it.tone) }

    @Test
    fun strokesAreDeterministicAndInBounds() {
        assertEquals(strokes(7L), strokes(7L))
        assertNotEquals(strokes(7L), strokes(8L))
        assertEquals(50, strokes(7L).size)
        assertTrue(strokes(7L).all { it[0] in 10f..90f && it[1] in 20f..60f })
        assertEquals(dotField(3L, 20, 0f, 0f, 5f, 5f).map { it.x }, dotField(3L, 20, 0f, 0f, 5f, 5f).map { it.x })
    }

    @Test
    fun fitContainCentresAndCoverFills() {
        val contain = fitTransform(240f, 240f, 200f, 100f, Fit.Contain, 0.5f)
        assertEquals(100f / 240f, contain.scale, 1e-6f)
        assertEquals(50f, contain.dx, 1e-3f)
        val cover = fitTransform(360f, 200f, 360f, 100f, Fit.Cover, 1f)
        assertEquals(1f, cover.scale, 1e-6f)
        assertEquals(-100f, cover.dy, 1e-3f)
    }

    @Test
    fun cacheKeysAndEviction() {
        assertEquals(ArtKey("Todos", 400, 400, false, 1), ArtKey("Todos", 400, 400, false, 1))
        assertNotEquals(ArtKey("Todos", 400, 400, false, 1), ArtKey("Todos", 400, 400, true, 1))
        val lru = ByteLru<String, ByteArray>(100) { it.size.toLong() }
        lru.put("a", ByteArray(60)); lru.put("b", ByteArray(60))
        assertNull(lru.get("a"))
        assertNotNull(lru.get("b"))
        lru.put("c", ByteArray(30))
        assertNotNull(lru.get("b")); assertNotNull(lru.get("c"))
        assertEquals(2, lru.size)
    }

    @Test
    fun idleMotionStaysInRange() {
        assertEquals(0f, blinkAt(0.1f), 0f)
        assertEquals(1f, blinkAt(0.3f), 1e-6f)
        assertTrue((0..100).all { blinkAt(it / 100f) in 0f..1f && swayAt(it / 100f) in -1f..1f })
        assertFalse(blinkAt(0.55f) > 0f)
    }
}
