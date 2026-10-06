package app.cove.companion.data.media

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PhotoMathTest {
    @Test fun wideKeepsAspect() = assertEquals(PhotoBox(608, false), photoBox(2048, 1152, 1080))
    @Test fun squareIsSquare() = assertEquals(PhotoBox(1080, false), photoBox(1000, 1000, 1080))
    @Test fun tallIsCappedAndCropped() = assertEquals(PhotoBox(1350, true), photoBox(1000, 3000, 1080))
    @Test fun justUnderCapIsNotCropped() = assertEquals(PhotoBox(1350, false), photoBox(1000, 1250, 1080))
    @Test fun unknownSizeUsesFourThree() = assertEquals(PhotoBox(810, false), photoBox(0, 0, 1080))
    @Test fun zeroWidthIsEmpty() = assertEquals(PhotoBox(0, false), photoBox(100, 100, 0))

    @Test fun decodeNeverUpscales() {
        assertEquals(1000 to 500, decodeSizeForWidth(1000, 500, 1080))
        assertEquals(1080 to 720, decodeSizeForWidth(2048, 1365, 1080))
        assertEquals(540 to 270, decodeSizeForWidth(1080, 540, 540))
    }

    @Test fun orientationSwapsSides() {
        assertEquals(300 to 400, orientedSize(400, 300, 6))
        assertEquals(400 to 300, orientedSize(400, 300, 1))
    }

    @Test fun cacheScalesWithMemoryClassWithinBounds() {
        assertEquals(8 * 1024 * 1024, photoCacheBytes(16))
        assertEquals(256 * 1024 * 1024 / 6, photoCacheBytes(256))
        assertEquals(64 * 1024 * 1024, photoCacheBytes(1024))
        assertTrue(photoCacheBytes(192) < photoCacheBytes(512))
    }
}
