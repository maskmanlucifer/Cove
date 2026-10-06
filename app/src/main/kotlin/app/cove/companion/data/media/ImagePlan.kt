package app.cove.companion.data.media

import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge of a stored photo, in pixels. */
const val MAX_LONG_EDGE = 2048

/** Photos smaller than this are stored as picked. */
const val SKIP_BELOW_BYTES = 500_000L

/** Longest edge of a thumbnail. */
const val THUMB_EDGE = 320

/** WebP quality of thumbnails. */
const val THUMB_QUALITY = 70

/** Default "Balanced" photo quality; "High" is 90. */
const val QUALITY_BALANCED = 80

/** What to do with a picked photo. [skip] keeps the original bytes; otherwise decode at [width] x [height] and encode WebP. */
data class ImagePlan(val skip: Boolean, val width: Int, val height: Int, val lossless: Boolean, val quality: Int)

/** Scales (width, height) down so the long edge is at most [maxEdge]; never upscales. */
fun fitLongEdge(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    val long = max(width, height)
    if (long <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / long
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * Compression decision per PLAN 6c: keep files under 500 KB that already fit 2048 px, resize the rest to a
 * 2048 px long edge, and use lossless WebP for screenshot-like images so text stays sharp.
 */
fun planImage(width: Int, height: Int, bytes: Long, screenshotLike: Boolean, quality: Int = QUALITY_BALANCED): ImagePlan {
    val (w, h) = fitLongEdge(width, height, MAX_LONG_EDGE)
    val fits = w == width && h == height
    return ImagePlan(skip = fits && bytes < SKIP_BELOW_BYTES, width = w, height = h, lossless = screenshotLike, quality = quality)
}

/**
 * Screenshots and UI captures use few distinct colours; photos use thousands.
 * [pixels] is a sample of ARGB ints, compared after dropping the low 3 bits of each channel.
 */
fun isScreenshotLike(pixels: IntArray, maxDistinct: Int = 400): Boolean {
    if (pixels.isEmpty()) return false
    val seen = HashSet<Int>()
    for (p in pixels) {
        seen.add(p and 0xF8F8F8)
        if (seen.size > maxDistinct) return false
    }
    return true
}
