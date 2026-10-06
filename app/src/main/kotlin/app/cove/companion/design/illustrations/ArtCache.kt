package app.cove.companion.design.illustrations

import androidx.compose.ui.graphics.ImageBitmap

/** How a scene is fitted into the box it is drawn in. */
internal enum class Fit { Contain, Cover }

/** Scale and offset that place a [artW] x [artH] virtual scene in a [w] x [h] box. */
internal class FitXform(val scale: Float, val dx: Float, val dy: Float)

/** [anchorY] 0..1 chooses which part of the art stays visible when [Fit.Cover] crops (0 = top, 1 = bottom). */
internal fun fitTransform(artW: Float, artH: Float, w: Float, h: Float, fit: Fit, anchorY: Float): FitXform {
    val sx = w / artW
    val sy = h / artH
    val s = if (fit == Fit.Cover) maxOf(sx, sy) else minOf(sx, sy)
    return FitXform(s, (w - artW * s) / 2f, (h - artH * s) * anchorY)
}

/** Identity of one rendered bitmap: the same scene at the same pixel size, theme and variant is rendered once. */
internal data class ArtKey(val id: String, val wPx: Int, val hPx: Int, val dark: Boolean, val variant: Int)

/**
 * Least-recently-used map bounded by total bytes, so a few scenes stay in memory and the rest are dropped.
 *
 * @param sizeOf bytes held by one value.
 */
internal class ByteLru<K : Any, V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map.remove(key)?.let { bytes -= sizeOf(it) }
        map[key] = value
        bytes += sizeOf(value)
        val it = map.entries.iterator()
        while (bytes > maxBytes && map.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key == key) continue
            bytes -= sizeOf(e.value)
            it.remove()
        }
    }

    val size: Int @Synchronized get() = map.size
}

/** Process-wide cache of finished scene bitmaps (24 MB at most). */
internal object ArtCache {
    private val lru = ByteLru<ArtKey, ImageBitmap>(24L shl 20) { it.width * it.height * 4L }
    fun get(key: ArtKey) = lru.get(key)
    fun put(key: ArtKey, bmp: ImageBitmap) = lru.put(key, bmp)
}
