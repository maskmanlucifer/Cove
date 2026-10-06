package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The flower kinds of the meadow, from the owner's references. */
internal enum class Bloom { Orange, Yellow, Lilac, Pink, White, Daisy }

private fun rad(deg: Float) = deg * PI.toFloat() / 180f

/** One petal: an oval of half-length [len] and half-width [wid] whose base touches (cx, cy) and points [deg]. */
private fun Painter.petal(cx: Float, cy: Float, deg: Float, len: Float, wid: Float, c: Color, a: Float = 1f) {
    val r = rad(deg)
    val mx = cx + cos(r) * len
    val my = cy + sin(r) * len
    ds.rotateAround(deg, mx, my) { fill(oval(mx, my, len, wid), c, a) }
}

/** A bloom of radius [s] centred at (x, y). The painter's seed and (x, y) vary the petal angle so a field is never uniform. */
internal fun Painter.flower(kind: Bloom, x: Float, y: Float, s: Float, turn: Float = (x * 37f + y * 17f) % 60f) {
    when (kind) {
        Bloom.Orange -> {
            val c = pal.orange
            repeat(6) { petal(x, y, turn + it * 60f, s * 0.5f, s * 0.27f, if (it % 2 == 0) c else lerp(c, pal.sun, 0.35f)) }
            fill(oval(x, y, s * 0.2f), pal.clay)
            fill(oval(x - s * 0.04f, y - s * 0.05f, s * 0.08f), pal.sun, 0.9f)
        }
        Bloom.Yellow -> {
            val c = pal.sun
            fill(path {
                moveTo(x - s * 0.55f, y - s * 0.35f)
                quadraticTo(x - s * 0.4f, y + s * 0.5f, x, y + s * 0.5f)
                quadraticTo(x + s * 0.4f, y + s * 0.5f, x + s * 0.55f, y - s * 0.35f)
                quadraticTo(x + s * 0.28f, y - s * 0.1f, x + s * 0.2f, y - s * 0.5f)
                quadraticTo(x, y - s * 0.12f, x - s * 0.2f, y - s * 0.5f)
                quadraticTo(x - s * 0.28f, y - s * 0.1f, x - s * 0.55f, y - s * 0.35f)
                close()
            }, vgrad(y - s * 0.5f, y + s * 0.5f, lerp(c, pal.warmWhite, 0.3f), lerp(c, pal.orange, 0.45f)))
        }
        Bloom.Lilac -> {
            val c = pal.lilac
            listOf(Offset(-0.3f, 0.1f), Offset(0.3f, 0.05f), Offset(0f, -0.3f), Offset(0.02f, 0.3f)).forEach { o ->
                fill(oval(x + o.x * s, y + o.y * s, s * 0.42f), Brush.radialGradient(0f to lerp(c, pal.warmWhite, 0.45f), 1f to c, center = Offset(x + o.x * s - s * 0.1f, y + o.y * s - s * 0.12f), radius = s * 0.6f))
            }
        }
        Bloom.Pink -> {
            val c = pal.pink
            repeat(5) { petal(x, y, turn + it * 72f, s * 0.42f, s * 0.3f, if (it % 2 == 0) c else lerp(c, pal.warmWhite, 0.25f)) }
            fill(oval(x, y, s * 0.2f), lerp(pal.pink, pal.leafDeep, 0.3f))
            fill(oval(x, y, s * 0.09f), pal.sun)
        }
        Bloom.White -> {
            repeat(3) {
                val a = turn + it * 120f - 90f
                petal(x, y, a, s * 0.5f, s * 0.34f, pal.warmWhite)
                petal(x, y, a, s * 0.36f, s * 0.2f, lerp(pal.warmWhite, pal.leafSoft, 0.7f), 0.7f)
            }
            fill(oval(x, y, s * 0.2f), pal.sun)
            fill(oval(x - s * 0.04f, y - s * 0.05f, s * 0.09f), pal.glow)
        }
        Bloom.Daisy -> {
            repeat(5) { petal(x, y, turn + it * 72f, s * 0.38f, s * 0.2f, lerp(pal.warmWhite, pal.lilacSoft, 0.35f)) }
            fill(oval(x, y, s * 0.14f), pal.sun)
        }
    }
}

/** A stemmed flower: curved stem to (x, groundY), a leaf, then the bloom. */
internal fun Painter.stemmed(kind: Bloom, x: Float, y: Float, s: Float, groundY: Float, sway: Float = 0f) {
    val sx = x + sway
    line(path { moveTo(sx, groundY); quadraticTo(sx - sway * 0.2f, (y + groundY) / 2f, x, y) }, pal.leafDeep, 1.3f, 0.85f)
    if (groundY - y > s * 2.2f) leafAt(sx, groundY - (groundY - y) * 0.35f, if (sway >= 0f) -35f else -145f, s * 0.9f, s * 0.32f, pal.leaf)
    flower(kind, x, y, s)
}

/**
 * Scatters flowers over a box, drawn back to front (small and high first). Each pick is seeded.
 *
 * @param stems draw stems down to [groundY] (true for foreground flowers whose stems show).
 */
internal fun Painter.scatter(
    sd: Long, count: Int, x0: Float, x1: Float, y0: Float, y1: Float, size0: Float, size1: Float,
    kinds: List<Bloom>, stems: Boolean = false, groundY: Float = y1 + 14f,
) {
    val r = Random(seed + sd)
    val items = List(count) { Triple(x0 + r.nextFloat() * (x1 - x0), y0 + r.nextFloat() * (y1 - y0), kinds[r.nextInt(kinds.size)]) }.sortedBy { it.second }
    for ((x, y, k) in items) {
        val t = (y - y0) / (y1 - y0).coerceAtLeast(1f)
        val s = size0 + (size1 - size0) * t
        if (stems) stemmed(k, x, y, s, y + s * 2.4f + 4f * t, (r.nextFloat() - 0.5f) * 4f) else flower(k, x, y, s)
    }
}

internal fun Painter.mushroom(x: Float, y: Float, s: Float, cap: Color = pal.coral) {
    fill(oval(x, y + 1f, s * 0.7f, s * 0.14f), pal.shade)
    fill(path { moveTo(x - s * 0.2f, y); quadraticTo(x - s * 0.15f, y - s * 0.5f, x - s * 0.1f, y - s * 0.6f); lineTo(x + s * 0.1f, y - s * 0.6f); quadraticTo(x + s * 0.15f, y - s * 0.5f, x + s * 0.2f, y); close() }, pal.cream)
    fill(path {
        moveTo(x - s * 0.62f, y - s * 0.55f)
        quadraticTo(x - s * 0.55f, y - s * 1.25f, x, y - s * 1.25f)
        quadraticTo(x + s * 0.55f, y - s * 1.25f, x + s * 0.62f, y - s * 0.55f)
        quadraticTo(x, y - s * 0.4f, x - s * 0.62f, y - s * 0.55f)
        close()
    }, vgrad(y - s * 1.25f, y - s * 0.4f, lerp(cap, pal.warmWhite, 0.15f), cap))
    ds.drawCircle(pal.cream, s * 0.09f, Offset(x - s * 0.22f, y - s * 0.92f), 0.9f)
    ds.drawCircle(pal.cream, s * 0.07f, Offset(x + s * 0.2f, y - s * 0.85f), 0.9f)
    ds.drawCircle(pal.cream, s * 0.06f, Offset(x + s * 0.02f, y - s * 1.08f), 0.9f)
}

internal fun Painter.stone(x: Float, y: Float, rx: Float, ry: Float = rx * 0.6f, c: Color = pal.sage) {
    fill(oval(x + 1f, y + ry * 0.45f, rx * 1.1f, ry * 0.45f), pal.shade)
    fill(oval(x, y, rx, ry), vgrad(y - ry, y + ry, lerp(c, pal.warmWhite, 0.4f), lerp(c, pal.olive, 0.3f)))
    fill(oval(x - rx * 0.3f, y - ry * 0.4f, rx * 0.35f, ry * 0.22f), Color.White, 0.3f)
}

/** A seedling: two leaves on a short stem. */
internal fun Painter.sprout(x: Float, y: Float, s: Float) {
    line(path { moveTo(x, y); quadraticTo(x - s * 0.1f, y - s * 0.6f, x, y - s) }, pal.leafDeep, 1.6f)
    leafAt(x, y - s * 0.95f, -150f, s * 0.7f, s * 0.26f, pal.leafLight)
    leafAt(x, y - s * 0.75f, -30f, s * 0.8f, s * 0.3f, pal.leaf)
}

/** A butterfly with wings at [flap] (0..1). */
internal fun Painter.butterfly(x: Float, y: Float, s: Float, c: Color, rot: Float = -20f) = at(x, y, s, rot) {
    fill(oval(-3.5f, -2f, 3.8f, 5f), c, 0.95f)
    fill(oval(3.5f, -2f, 3.8f, 5f), c, 0.95f)
    fill(oval(-2.8f, 3.2f, 2.6f, 3.2f), lerp(c, pal.warmWhite, 0.3f), 0.95f)
    fill(oval(2.8f, 3.2f, 2.6f, 3.2f), lerp(c, pal.warmWhite, 0.3f), 0.95f)
    fill(rrect(-0.5f, -3f, 0.5f, 6f, 0.5f), pal.ink, 0.7f)
    line(path { moveTo(0f, -3f); quadraticTo(-2f, -7f, -4f, -7.5f) }, pal.ink, 0.5f, 0.6f)
    line(path { moveTo(0f, -3f); quadraticTo(2f, -7f, 4f, -7.5f) }, pal.ink, 0.5f, 0.6f)
}

/** A hanging or standing lantern body, lit when [lit]. Origin is the top of its handle. */
internal fun Painter.lanternProp(x: Float, y: Float, k: Float = 1f) = at(x, y, k) {
    glow(0f, 14f, 34f, pal.glow, if (pal.dark) 0.8f else 0.7f)
    line(path { moveTo(-5f, 2f); quadraticTo(0f, -7f, 5f, 2f) }, pal.olive, 1.4f)
    fill(rrect(-6f, 1f, 6f, 4f, 1.5f), pal.olive)
    fill(rrect(-8f, 4f, 8f, 25f, 5f), vgrad(4f, 25f, pal.glow, pal.sun))
    fill(oval(0f, 14f, 4f, 6f), Color.White, 0.55f)
    line(path { moveTo(0f, 4f); lineTo(0f, 25f) }, pal.olive, 0.7f, 0.5f)
    line(path { moveTo(-8f, 12f); lineTo(8f, 12f) }, pal.olive, 0.7f, 0.5f)
    fill(rrect(-8f, 25f, 8f, 28f, 1.5f), pal.olive)
}

/** Small round fireflies/pollen glow dots. */
internal fun Painter.fireflies(sd: Long, count: Int, l: Float, t: Float, r: Float, b: Float) {
    val rnd = Random(seed + sd)
    repeat(count) {
        val x = l + rnd.nextFloat() * (r - l)
        val y = t + rnd.nextFloat() * (b - t)
        glow(x, y, 6f, pal.glow, 0.8f)
        ds.drawCircle(pal.cream, 0.9f, Offset(x, y), 0.95f)
    }
}
