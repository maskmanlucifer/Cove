package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Brush kit the scenes paint with, in virtual units (the whole scene is scaled uniformly). Wraps one [DrawScope]
 * plus the [pal]ette; every random choice is seeded, so a scene always paints identically.
 */
internal class Painter(val ds: DrawScope, val pal: IllusPalette, val seed: Long = 7L) {
    /** The mascot standing in this scene, or null; drawn where the scene calls [mascot]. */
    var spot: MascotSpot? = null
    var showMascot = true

    /** Hill greens for a layer [depth] (0 far, 1 near): a lighter top and deeper low colour, hazed toward [haze] when far. */
    fun greens(depth: Float, haze: Color = pal.lilacSoft): Pair<Color, Color> {
        val base = lerp(lerp(pal.leafLight, pal.leaf, 0.4f), pal.grassDark, depth * 0.85f)
        val hazed = lerp(base, haze, (1f - depth) * if (pal.dark) 0.2f else 0.5f)
        return lerp(hazed, pal.warmWhite, if (pal.dark) 0.02f else 0.16f) to lerp(hazed, pal.leafDeep, 0.38f)
    }

    fun fill(p: Path, c: Color, a: Float = 1f) = ds.drawPath(p, c, a)

    fun fill(p: Path, b: Brush, a: Float = 1f) = ds.drawPath(p, b, a)

    fun line(p: Path, c: Color, w: Float = 1.2f, a: Float = 1f) =
        ds.drawPath(p, c, a, Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

    fun clip(p: Path, block: () -> Unit) = ds.clipPath(p) { block() }

    fun at(x: Float, y: Float, k: Float = 1f, deg: Float = 0f, block: () -> Unit) = ds.withTransform({
        translate(x, y)
        if (deg != 0f) rotate(deg, Offset.Zero)
        if (k != 1f) scale(k, k, Offset.Zero)
    }) { block() }

    fun glow(cx: Float, cy: Float, r: Float, c: Color, a: Float = 1f) =
        fill(oval(cx, cy, r), Brush.radialGradient(0f to c.copy(alpha = c.alpha * a), 1f to c.copy(alpha = 0f), center = Offset(cx, cy), radius = r))

    /** Soft contact shadow on the ground. */
    fun groundShadow(cx: Float, cy: Float, rx: Float, ry: Float = rx * 0.16f) =
        fill(oval(cx, cy, rx, ry), Brush.radialGradient(0f to pal.shade, 1f to pal.shade.copy(alpha = 0f), center = Offset(cx, cy), radius = rx))

    /** Short curved painted strokes, [c1] to [c2] by tone. */
    fun strokes(list: List<StrokeSpec>, c1: Color, c2: Color) {
        for (s in list) {
            val a = s.angle * PI.toFloat() / 180f
            val ex = s.x + cos(a) * s.len
            val ey = s.y + sin(a) * s.len
            val nx = -sin(a) * s.len * s.bend
            val ny = cos(a) * s.len * s.bend
            line(path { moveTo(s.x, s.y); quadraticTo((s.x + ex) / 2 + nx, (s.y + ey) / 2 + ny, ex, ey) }, lerp(c1, c2, s.tone), s.width, s.alpha)
        }
    }

    fun dots(list: List<DotSpec>, c1: Color, c2: Color, alpha: Float = 1f) {
        for (d in list) ds.drawCircle(lerp(c1, c2, d.tone), d.r, Offset(d.x, d.y), d.alpha * alpha)
    }

    /** Faint paper grain over the whole [w] x [h] scene. */
    fun grain(w: Float, h: Float, count: Int = (w * h / 38f).toInt()) {
        val r = Random(seed * 31 + 5)
        val dark = ArrayList<Offset>(count / 2)
        val light = ArrayList<Offset>(count / 2)
        repeat(count) { (if (it % 2 == 0) dark else light) += Offset(r.nextFloat() * w, r.nextFloat() * h) }
        ds.drawPoints(dark, PointMode.Points, pal.ink.copy(alpha = if (pal.dark) 0.05f else 0.045f), 0.9f, StrokeCap.Round)
        ds.drawPoints(light, PointMode.Points, pal.cream.copy(alpha = if (pal.dark) 0.05f else 0.2f), 0.9f, StrokeCap.Round)
    }

    /**
     * A rolling hill: a smooth ridge through [pts], filled down to [bottom] with a gradient, painted with grass
     * strokes and a thin sunlit rim.
     */
    fun hill(
        pts: List<Offset>, bottom: Float, top: Color, low: Color, tex1: Color, tex2: Color,
        strokesCount: Int = 90, rim: Color? = null, sd: Long = 1L,
    ) {
        val body = smoothPath(pts, bottom)
        val minY = pts.minOf { it.y }
        fill(body, vgrad(minY, bottom, top, low))
        if (strokesCount > 0) clip(body) {
            strokes(
                strokeField(seed + sd, strokesCount, pts.first().x, minY, pts.last().x, bottom, 3f..8f, -90f, 26f),
                tex1, tex2,
            )
        }
        if (rim != null) line(smoothPath(pts), rim, 1.4f, 0.55f)
    }

    /** Tapered grass blades from y = [baseY], spread over [x0]..[x1]. */
    fun blades(
        x0: Float, x1: Float, baseY: Float, count: Int, minH: Float, maxH: Float, c1: Color, c2: Color,
        sd: Long = 3L, lean: Float = 3f, wid: Float = 1.5f,
    ) {
        val r = Random(seed + sd)
        repeat(count) {
            val x = x0 + r.nextFloat() * (x1 - x0)
            val y = baseY + r.nextFloat() * 4f
            val h = minH + r.nextFloat() * (maxH - minH)
            val dx = (r.nextFloat() * 2f - 1f) * lean
            val w = wid * (0.7f + r.nextFloat() * 0.6f)
            fill(
                path {
                    moveTo(x - w, y)
                    quadraticTo(x - w * 0.3f + dx * 0.2f, y - h * 0.6f, x + dx, y - h)
                    quadraticTo(x + w * 0.5f + dx * 0.3f, y - h * 0.55f, x + w, y)
                    close()
                },
                lerp(c1, c2, r.nextFloat()), 0.92f,
            )
        }
    }

    /** Leaf shape rooted at (bx, by) pointing [deg] degrees (0 = right, -90 = up). */
    fun leafAt(bx: Float, by: Float, deg: Float, len: Float, wid: Float, c: Color, vein: Boolean = true, a: Float = 1f) {
        fill(leafPath(bx, by, deg, len, wid), c, a)
        if (vein) {
            val r = deg * PI.toFloat() / 180f
            line(path { moveTo(bx, by); lineTo(bx + cos(r) * len * 0.82f, by + sin(r) * len * 0.82f) }, pal.leafDeep, 0.7f, 0.45f * a)
        }
    }

    fun cloud(cx: Float, cy: Float, w: Float, c: Color = pal.warmWhite, a: Float = 0.95f) {
        val h = w * 0.28f
        val p = path {
            addRoundRect(RoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, CornerRadius(h / 2)))
            addOval(Rect(cx - w * 0.30f, cy - h * 1.15f, cx + w * 0.04f, cy + h * 0.25f))
            addOval(Rect(cx - w * 0.10f, cy - h * 1.5f, cx + w * 0.28f, cy + h * 0.2f))
        }
        fill(p, vgrad(cy - h * 1.5f, cy + h / 2, c, lerp(c, pal.lilacSoft, if (pal.dark) 0.25f else 0.55f)), a)
        fill(oval(cx - w * 0.12f, cy - h * 0.7f, w * 0.12f, h * 0.3f), Color.White, if (pal.dark) 0.06f else 0.5f)
    }

    fun sparkle(cx: Float, cy: Float, r: Float, c: Color, a: Float = 1f) = fill(
        path {
            moveTo(cx, cy - r); quadraticTo(cx, cy, cx + r, cy); quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy); quadraticTo(cx, cy, cx, cy - r); close()
        },
        c, a,
    )

    fun sun(cx: Float, cy: Float, r: Float, rays: Boolean = true) {
        glow(cx, cy, r * 3.4f, pal.glow, if (pal.dark) 0.5f else 0.85f)
        if (rays) repeat(12) {
            val a = it * (PI.toFloat() / 6f) + 0.2f
            line(path { moveTo(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f); lineTo(cx + cos(a) * r * 1.75f, cy + sin(a) * r * 1.75f) }, pal.sun, 1.8f, 0.55f)
        }
        fill(oval(cx, cy, r), Brush.radialGradient(0f to pal.glow, 1f to pal.sun, center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 1.5f))
    }

    fun moon(cx: Float, cy: Float, r: Float) {
        glow(cx, cy, r * 3f, pal.glow, 0.35f)
        fill(oval(cx, cy, r), Brush.radialGradient(0f to pal.cream, 1f to pal.glow, center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 1.6f))
        fill(oval(cx + r * 0.3f, cy + r * 0.25f, r * 0.22f), pal.sun, 0.25f)
        fill(oval(cx - r * 0.35f, cy - r * 0.15f, r * 0.15f), pal.sun, 0.2f)
        fill(oval(cx + r * 0.05f, cy - r * 0.45f, r * 0.1f), pal.sun, 0.2f)
    }

    /** Draws the scene's mascot (a no-op for scenes without one or for strips that skip it). */
    fun mascot() {
        val s = spot ?: return
        if (showMascot) at(s.x, s.y, s.scale) { drawMascotBody(this, s.pose, s.flip) }
    }
}

internal fun vgrad(y0: Float, y1: Float, top: Color, bottom: Color): Brush = Brush.verticalGradient(0f to top, 1f to bottom, startY = y0, endY = y1)

internal fun oval(cx: Float, cy: Float, rx: Float, ry: Float = rx) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

internal fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) = Path().apply { addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad))) }

internal inline fun path(block: Path.() -> Unit) = Path().apply(block)

/** Catmull-Rom curve through [pts]; with [closeToY] it is closed down to that y for filling. */
internal fun smoothPath(pts: List<Offset>, closeToY: Float? = null): Path = path {
    moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y,
        )
    }
    if (closeToY != null) { lineTo(pts.last().x, closeToY); lineTo(pts.first().x, closeToY); close() }
}

/** Leaf shape with its base at (bx, by) pointing [deg] degrees, [len] long and [wid] wide. */
internal fun leafPath(bx: Float, by: Float, deg: Float, len: Float, wid: Float): Path {
    val a = deg * PI.toFloat() / 180f
    val ca = cos(a); val sa = sin(a)
    fun p(x: Float, y: Float) = Offset(bx + x * ca - y * sa, by + x * sa + y * ca)
    val c1 = p(len * 0.3f, -wid); val c2 = p(len * 0.8f, -wid * 0.6f); val tip = p(len, 0f)
    val c3 = p(len * 0.8f, wid * 0.6f); val c4 = p(len * 0.3f, wid)
    return path {
        moveTo(bx, by)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, bx, by)
        close()
    }
}

internal fun pts(vararg xy: Float): List<Offset> = List(xy.size / 2) { Offset(xy[it * 2], xy[it * 2 + 1]) }

/** Rotates around a pivot inside [block] (used by the live mascot layer). */
internal fun DrawScope.rotateAround(deg: Float, px: Float, py: Float, block: DrawScope.() -> Unit) = rotate(deg, Offset(px, py), block)
