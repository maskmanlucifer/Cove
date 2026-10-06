package app.cove.companion.data

import app.cove.companion.data.insights.SearchMath
import app.cove.companion.data.media.fitLongEdge
import app.cove.companion.data.media.isScreenshotLike
import app.cove.companion.data.media.planImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalEngineTest {
    @Test fun smallPhotoIsKept() = assertTrue(planImage(1600, 1200, 300_000, false).skip)

    @Test fun bigFileIsReencoded() {
        val p = planImage(1600, 1200, 2_000_000, false, 90)
        assertFalse(p.skip)
        assertEquals(90, p.quality)
    }

    @Test fun largePhotoShrinksToLongEdge() {
        val p = planImage(4000, 3000, 200_000, false)
        assertFalse(p.skip)
        assertEquals(2048 to 1536, p.width to p.height)
        assertEquals(1500 to 2048, fitLongEdge(3000, 4096, 2048))
    }

    @Test fun neverUpscales() = assertEquals(320 to 200, fitLongEdge(320, 200, 2048))

    @Test fun screenshotDetection() {
        assertTrue(isScreenshotLike(IntArray(4096) { if (it % 2 == 0) 0xFFFFFF else 0x000000 }))
        assertFalse(isScreenshotLike(IntArray(4096) { it * 4099 }))
    }

    @Test fun ftsQuery() {
        assertEquals("\"slow*\" \"sunday*\"", SearchMath.ftsQuery("Slow, Sunday!"))
        assertNull(SearchMath.ftsQuery(" !!! "))
        assertEquals(2, SearchMath.ftsQuery("a b c d", maxTerms = 2)!!.split(" ").size)
        assertEquals("\"it*\" \"s*\"", SearchMath.ftsQuery("it\"s"))
    }

    @Test fun cosineAndBytes() {
        assertEquals(1f, SearchMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-5f)
        assertEquals(0f, SearchMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-5f)
        assertEquals(0f, SearchMath.cosine(floatArrayOf(0f), floatArrayOf(0f)), 0f)
        val v = floatArrayOf(0.5f, -1.25f)
        assertEquals(v.toList(), SearchMath.fromBytes(SearchMath.toBytes(v)).toList())
    }

    @Test fun rankingFusesBothLists() {
        val hits = SearchMath.rank(
            keyword = listOf("a" to 3, "b" to 1, "c" to 1),
            semantic = listOf("b" to 0.9f, "d" to 0.8f, "e" to 0.1f),
        )
        assertEquals("b", hits.first().entryId)
        assertTrue(hits.none { it.entryId == "e" })
        assertEquals(setOf("a", "b", "c", "d"), hits.map { it.entryId }.toSet())
    }

    @Test fun keywordScoreCountsPrefixes() =
        assertEquals(5, SearchMath.keywordScore("Tomatoes, tomato soup and a tom", listOf("tomat", "tom")))
}
