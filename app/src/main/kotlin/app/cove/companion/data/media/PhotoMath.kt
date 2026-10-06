package app.cove.companion.data.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tallest a journal photo is drawn, as a multiple of its width; taller photos are centre-cropped to this. */
const val MAX_TALL_RATIO = 1.25f

/** Shape used before a photo's real size is known. */
const val FALLBACK_ASPECT = 4f / 3f

/** How a photo is laid out in the content column: [heightPx] for the box, [cropped] when the picture is cut to fit. */
data class PhotoBox(val heightPx: Int, val cropped: Boolean)

/** Height of a full-width photo of [width] x [height] drawn [widthPx] wide, capped at [maxRatio] x the width. */
fun photoBox(width: Int, height: Int, widthPx: Int, maxRatio: Float = MAX_TALL_RATIO): PhotoBox {
    if (widthPx <= 0) return PhotoBox(0, false)
    val natural = if (width > 0 && height > 0) widthPx.toFloat() * height / width else widthPx / FALLBACK_ASPECT
    val cap = widthPx * maxRatio
    return if (natural > cap) PhotoBox(cap.roundToInt(), true) else PhotoBox(max(1, natural.roundToInt()), false)
}

/** Pixel size to decode a [width] x [height] photo at so it fills [widthPx]; never larger than the source. */
fun decodeSizeForWidth(width: Int, height: Int, widthPx: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || widthPx <= 0) return 1 to 1
    val scale = min(1.0, widthPx.toDouble() / width)
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** Swaps the stored size when an EXIF [orientation] turns the picture on its side (5-8). */
fun orientedSize(width: Int, height: Int, orientation: Int): Pair<Int, Int> =
    if (orientation in 5..8) height to width else width to height

/** Bytes the in-memory photo cache may hold: a sixth of the app's [memoryClassMb], between 8 and 64 MB. */
fun photoCacheBytes(memoryClassMb: Int): Int =
    (memoryClassMb.toLong() * 1024 * 1024 / 6).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()
