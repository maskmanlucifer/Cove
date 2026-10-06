package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** A little paper pad leaning in the grass, with two ticked and one empty tick-box. Origin at its base centre. */
internal fun Painter.notebook(x: Float, y: Float) = at(x, y, 1f, -5f) {
    fill(oval(2f, 1f, 30f, 4f), pal.shade)
    fill(rrect(-25f, -58f, 25f, 0f, 5f), pal.mix(pal.leaf, pal.leafDeep, 0.2f))
    fill(rrect(-22f, -55f, 24f, -2f, 4f), pal.cream)
    repeat(3) { i ->
        val ry = -42f + i * 14f
        fill(rrect(-16f, ry - 5f, -6f, ry + 5f, 2.5f), pal.sheet)
        line(path { addRoundRect(androidx.compose.ui.geometry.RoundRect(-16f, ry - 5f, -6f, ry + 5f, androidx.compose.ui.geometry.CornerRadius(2.5f))) }, pal.olive, 1f, 0.7f)
        if (i < 2) line(path { moveTo(-14f, ry); lineTo(-11.5f, ry + 2.6f); lineTo(-7.5f, ry - 3.4f) }, pal.leafDeep, 1.8f)
        line(path { moveTo(-1f, ry); lineTo(if (i == 1) 12f else 17f, ry) }, pal.olive, 1.4f, 0.45f)
    }
    for (i in 0 until 5) ds.drawCircle(pal.olive, 1.3f, Offset(-24f, -50f + i * 11f), 0.7f)
    sprout(20f, -58f, 9f)
}

/** A signpost holding a tear-off calendar leaf with a leaf in place of the date. */
internal fun Painter.calendarPost(x: Float, y: Float) = at(x, y) {
    fill(oval(0f, 1f, 16f, 3f), pal.shade)
    fill(rrect(-2.5f, -78f, 2.5f, 0f, 2f), pal.clay)
    at(0f, -80f, 1f, -4f) {
        fill(rrect(-27f, -36f, 27f, 24f, 6f), pal.shade, 0.6f)
        fill(rrect(-27f, -38f, 27f, 22f, 6f), pal.sheet)
        fill(path { moveTo(-27f, -29f); quadraticTo(-27f, -38f, -21f, -38f); lineTo(21f, -38f); quadraticTo(27f, -38f, 27f, -29f); lineTo(27f, -20f); lineTo(-27f, -20f); close() }, pal.coral)
        for (sx in listOf(-12f, 12f)) { fill(rrect(sx - 2f, -43f, sx + 2f, -33f, 2f), pal.olive) }
        leafAt(-8f, 8f, -50f, 24f, 8.5f, pal.leaf)
        repeat(3) { ds.drawCircle(pal.olive, 1.5f, Offset(-16f + it * 6f, 16f), 0.35f) }
    }
}

/** A glass jar of coins with a sprout growing out of it. */
internal fun Painter.jar(x: Float, y: Float) = at(x, y) {
    fill(oval(0f, 1f, 24f, 4f), pal.shade)
    val glass = rrect(-18f, -48f, 18f, 0f, 9f)
    fill(glass, vgrad(-48f, 0f, pal.sheet.copy(alpha = 0.55f), pal.mix(pal.sky, pal.sheet, 0.55f).copy(alpha = 0.5f)))
    clip(glass) {
        for ((cx, cy) in listOf(-8f to -4f, 6f to -5f, -1f to -11f, 9f to -14f, -9f to -16f)) {
            fill(oval(cx, cy, 8f, 3.4f), vgrad(cy - 3f, cy + 3f, pal.glow, pal.sun))
            line(path { moveTo(cx - 5f, cy); lineTo(cx + 5f, cy) }, pal.orange, 0.7f, 0.55f)
        }
        fill(rrect(-14f, -44f, -10f, -8f, 2f), Color.White, 0.5f)
    }
    line(path { addRoundRect(androidx.compose.ui.geometry.RoundRect(-18f, -48f, 18f, 0f, androidx.compose.ui.geometry.CornerRadius(9f))) }, pal.olive, 1f, 0.3f)
    fill(rrect(-14f, -53f, 14f, -47f, 3f), pal.clay)
    sprout(0f, -52f, 22f)
}

internal fun Painter.padlock(x: Float, y: Float, k: Float = 1f) = at(x, y, k) {
    line(path { moveTo(-6f, -8f); lineTo(-6f, -16f); quadraticTo(-6f, -24f, 0f, -24f); quadraticTo(6f, -24f, 6f, -16f); lineTo(6f, -8f) }, pal.olive, 3.2f)
    fill(rrect(-11f, -9f, 11f, 10f, 5f), vgrad(-9f, 10f, pal.glow, pal.sun))
    fill(oval(0f, -1f, 2.4f), pal.olive)
    fill(rrect(-0.9f, -1f, 0.9f, 5f, 0.9f), pal.olive)
    fill(rrect(-8f, -7f, -5f, 6f, 1.5f), Color.White, 0.35f)
}

internal fun Painter.envelope(x: Float, y: Float, k: Float = 1f, deg: Float = 0f) = at(x, y, k, deg) {
    fill(oval(0f, 14f, 22f, 3f), pal.shade)
    fill(rrect(-22f, -13f, 22f, 14f, 4f), vgrad(-13f, 14f, pal.sheet, pal.cream))
    line(path { moveTo(-22f, -9f); lineTo(0f, 5f); lineTo(22f, -9f) }, pal.olive, 1.1f, 0.5f)
    fill(oval(0f, 4f, 4.4f), pal.coral)
    leafAt(0f, 6f, -90f, 5.4f, 2f, pal.sheet, vein = false)
}

internal fun Painter.paperPlane(x: Float, y: Float, k: Float = 1f, deg: Float = -15f) = at(x, y, k, deg) {
    fill(path { moveTo(-20f, -2f); lineTo(20f, -10f); lineTo(-6f, 10f); close() }, pal.sheet)
    fill(path { moveTo(-20f, -2f); lineTo(20f, -10f); lineTo(-4f, 2f); close() }, pal.cream)
    fill(path { moveTo(-4f, 2f); lineTo(20f, -10f); lineTo(-6f, 10f); close() }, pal.mix(pal.lilacSoft, pal.cream, 0.4f))
    line(path { moveTo(-4f, 2f); lineTo(-8f, 9f) }, pal.olive, 0.8f, 0.4f)
}

/** Small dashed trail of dots along a curve from (x0,y0) via control to (x1,y1). */
internal fun Painter.dotTrail(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, n: Int = 14, c: Color = pal.olive) {
    for (i in 0..n) {
        val t = i / n.toFloat()
        val x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * cx + t * t * x1
        val y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * cy + t * t * y1
        ds.drawCircle(c, 1.2f, Offset(x, y), 0.55f)
    }
}

internal fun Painter.alarmClock(x: Float, y: Float, k: Float = 1f) = at(x, y, k) {
    fill(oval(0f, 1f, 18f, 3f), pal.shade)
    for (s in listOf(-1f, 1f)) { fill(oval(s * 12f, -33f, 7f, 5f), vgrad(-38f, -28f, pal.orange, pal.coral)) }
    fill(oval(0f, -17f, 17f), vgrad(-34f, 0f, pal.glow, pal.sun))
    fill(oval(0f, -17f, 13f), pal.sheet)
    line(path { moveTo(0f, -17f); lineTo(0f, -25f) }, pal.olive, 1.6f)
    line(path { moveTo(0f, -17f); lineTo(6f, -14f) }, pal.olive, 1.6f)
    repeat(4) { i -> val a = i * 1.5708f; ds.drawCircle(pal.olive, 0.8f, Offset(kotlin.math.cos(a) * 10f, -17f + kotlin.math.sin(a) * 10f), 0.6f) }
    fill(rrect(-12f, -2f, -8f, 3f, 1f), pal.olive); fill(rrect(8f, -2f, 12f, 3f, 1f), pal.olive)
}

/** A round lifebuoy with four coral and cream segments. */
internal fun Painter.lifebuoy(x: Float, y: Float, r: Float) = at(x, y) {
    fill(oval(2f, r * 0.98f, r * 1.05f, r * 0.2f), pal.shade)
    val ringOut = androidx.compose.ui.geometry.Rect(-r, -r, r, r)
    val w = r * 0.38f
    for (i in 0 until 8) {
        line(path { arcTo(androidx.compose.ui.geometry.Rect(-r + w / 2, -r + w / 2, r - w / 2, r - w / 2), i * 45f - 22f, 45f, true) }, if (i % 2 == 0) pal.coral else pal.sheet, w)
    }
    line(path { addOval(ringOut) }, pal.olive, 0.8f, 0.25f)
    line(path { addOval(androidx.compose.ui.geometry.Rect(-r + w, -r + w, r - w, r - w)) }, pal.olive, 0.8f, 0.25f)
}

/** A flower whose heart is a padlock. */
internal fun Painter.lockFlower(x: Float, y: Float, s: Float) {
    line(path { moveTo(x, y + s * 3.2f); quadraticTo(x + 4f, y + s * 1.6f, x, y) }, pal.leafDeep, 2.4f)
    leafAt(x + 1f, y + s * 2.4f, -25f, s * 1.4f, s * 0.46f, pal.leaf); leafAt(x, y + s * 1.8f, -155f, s * 1.3f, s * 0.42f, pal.leafLight)
    repeat(9) { i -> leafAt(x, y, i * 40f + 10f, s * 1.05f, s * 0.36f, if (i % 2 == 0) pal.pink else pal.mix(pal.pink, pal.sheet, 0.4f), vein = false) }
    fill(oval(x, y, s * 0.72f), pal.sheet)
    padlock(x, y + 3f, s * 0.052f)
}
