package app.cove.companion.design.components

import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** Pan and zoom of a picture inside a view: [scale] >= 1 around the view centre, then translated by ([x], [y]) pixels. */
data class Zoom(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    /** True when zoomed in, so a one-finger drag pans instead of paging. */
    val zoomed: Boolean get() = scale > 1.01f
}

/** Size of a [srcW] x [srcH] picture scaled to fit inside [viewW] x [viewH] (the picture's size at zoom 1). */
fun fitSize(srcW: Float, srcH: Float, viewW: Float, viewH: Float): Size {
    if (srcW <= 0f || srcH <= 0f) return Size(viewW, viewH)
    val s = min(viewW / srcW, viewH / srcH)
    return Size(srcW * s, srcH * s)
}

/** Size of a [srcW] x [srcH] picture scaled to cover [viewW] x [viewH] (used by the crop step). */
fun coverSize(srcW: Float, srcH: Float, viewW: Float, viewH: Float): Size {
    if (srcW <= 0f || srcH <= 0f) return Size(viewW, viewH)
    val s = max(viewW / srcW, viewH / srcH)
    return Size(srcW * s, srcH * s)
}

/** Keeps [z] within [minScale]..[maxScale] and the picture ([content] at zoom 1) covering as much of the view as it can. */
fun clampZoom(z: Zoom, view: Size, content: Size, maxScale: Float = 4f, minScale: Float = 1f): Zoom {
    val scale = z.scale.coerceIn(minScale, maxScale)
    val maxX = max(0f, (content.width * scale - view.width) / 2f)
    val maxY = max(0f, (content.height * scale - view.height) / 2f)
    return Zoom(scale, z.x.coerceIn(-maxX, maxX), z.y.coerceIn(-maxY, maxY))
}

/**
 * Applies a pinch of [factor] and a drag of ([panX], [panY]) to [z], keeping the picture point under the fingers
 * ([cx], [cy], measured from the view centre) fixed.
 */
fun zoomBy(z: Zoom, factor: Float, panX: Float, panY: Float, cx: Float, cy: Float, view: Size, content: Size, maxScale: Float = 4f, minScale: Float = 1f): Zoom {
    val scale = (z.scale * factor).coerceIn(minScale, maxScale)
    val k = scale / z.scale
    return clampZoom(Zoom(scale, cx - k * (cx - z.x) + panX, cy - k * (cy - z.y) + panY), view, content, maxScale, minScale)
}

/** Result of a double tap at ([cx], [cy]) from the view centre: back to [minScale] when zoomed, else in to [target]. */
fun doubleTapZoom(z: Zoom, cx: Float, cy: Float, view: Size, content: Size, target: Float = 2.5f, maxScale: Float = 4f, minScale: Float = 1f): Zoom =
    if (z.scale > minScale * 1.05f) Zoom(minScale) else zoomBy(z, target / z.scale, 0f, 0f, cx, cy, view, content, maxScale, minScale)
