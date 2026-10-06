package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.random.Random

/**
 * What Cove's companion is doing. Eyes carry all the expression (no mouth): oval size, tilt, gaze, closed lids.
 */
enum class MascotPose { Idle, Waving, Sleeping, Reading, Coin, Tending, Listening, Thinking, Celebrating, Blanket, Lantern, Lifting, Walking, Stargazing }

/** Where a scene stands its mascot: feet at ([x], [y]), [scale] 1 = 92 virtual units tall. */
internal class MascotSpot(val pose: MascotPose, val x: Float, val y: Float, val scale: Float = 1f, val flip: Boolean = false)

/** Eye shape: oval half-height [ry], gaze offset ([dx], [dy]), [tilt] degrees (mirrored per eye), [arc] 1 = happy ^, -1 = closed lid. */
private class Look(val dx: Float = 0f, val dy: Float = 0f, val tilt: Float = 0f, val ry: Float = 7.5f, val arc: Int = 0)

private fun lookOf(p: MascotPose) = when (p) {
    MascotPose.Idle -> Look()
    MascotPose.Waving -> Look(dy = -0.5f, ry = 8f)
    MascotPose.Sleeping, MascotPose.Blanket -> Look(arc = -1)
    MascotPose.Reading -> Look(dy = 3f, ry = 5.2f)
    MascotPose.Coin -> Look(dx = 2f, dy = -2f, ry = 8f)
    MascotPose.Tending -> Look(dx = 2.5f, dy = 2f, ry = 6.5f)
    MascotPose.Listening -> Look(dx = -2.8f, ry = 8.2f)
    MascotPose.Thinking -> Look(dx = 2f, dy = -3f, tilt = 10f, ry = 7f)
    MascotPose.Celebrating -> Look(arc = 1)
    MascotPose.Lantern -> Look(dx = 2f, dy = -1f, ry = 8f)
    MascotPose.Lifting -> Look(tilt = -13f, ry = 6f)
    MascotPose.Walking -> Look(dx = 1.5f, ry = 7.5f)
    MascotPose.Stargazing -> Look(dy = -3.5f, ry = 8.4f)
}

private fun armsOf(p: MascotPose): Pair<Offset, Offset> = when (p) {
    MascotPose.Idle, MascotPose.Stargazing -> Offset(-38f, -26f) to Offset(38f, -26f)
    MascotPose.Waving -> Offset(-38f, -26f) to Offset(47f, -84f)
    MascotPose.Sleeping -> Offset(-16f, -34f) to Offset(16f, -34f)
    MascotPose.Reading -> Offset(-19f, -29f) to Offset(19f, -29f)
    MascotPose.Coin -> Offset(-38f, -26f) to Offset(46f, -68f)
    MascotPose.Tending -> Offset(-38f, -26f) to Offset(47f, -40f)
    MascotPose.Listening -> Offset(-45f, -64f) to Offset(38f, -26f)
    MascotPose.Thinking -> Offset(-38f, -26f) to Offset(15f, -43f)
    MascotPose.Celebrating -> Offset(-47f, -86f) to Offset(47f, -86f)
    MascotPose.Blanket -> Offset(-25f, -43f) to Offset(25f, -43f)
    MascotPose.Lantern -> Offset(-38f, -26f) to Offset(46f, -46f)
    MascotPose.Lifting -> Offset(-38f, -26f) to Offset(45f, -62f)
    MascotPose.Walking -> Offset(-39f, -36f) to Offset(40f, -21f)
}

private val Eyes = listOf(-1f, 1f)
private const val EyeX = 13.5f
private const val EyeY = -58f

/** Antenna droop in degrees for sleepy poses. */
private fun droopOf(p: MascotPose) = if (p == MascotPose.Sleeping || p == MascotPose.Blanket) 14f else 0f

/**
 * The static part of the mascot (body, feet, arms, props): draw this into the cached scene bitmap. Origin is
 * the point between its feet. Eyes and antenna are drawn by [drawMascotLive] so they can move.
 */
internal fun drawMascotBody(p: Painter, pose: MascotPose, flip: Boolean) {
    p.ds.withTransform({ if (flip) scale(-1f, 1f, Offset.Zero) }) {
        val pal = p.pal
        val lean = if (pose == MascotPose.Walking) 3f else 0f
        p.groundShadow(0f, -1f, 34f, 5f)
        p.at(0f, 0f, 1f, lean) {
            feet(p, pose)
            body(p)
            arms(p, pose)
            props(p, pose)
            hands(p, pose)
        }
        if (pose == MascotPose.Celebrating) confetti(p)
    }
}

private fun feet(p: Painter, pose: MascotPose) {
    val col = p.pal.bodyDark
    val walking = pose == MascotPose.Walking
    p.fill(oval(-14f, if (walking) -3f else -5f, 11f, 5.5f), col)
    p.fill(oval(14f, if (walking) -11f else -5f, 11f, 5.5f), col)
    p.fill(oval(-16f, -6.5f, 6f, 2f), p.pal.bodyLight, 0.4f)
}

private fun body(p: Painter) {
    val pal = p.pal
    val shape = path { addRoundRect(RoundRect(-31f, -86f, 31f, -8f, CornerRadius(30f, 36f), CornerRadius(30f, 36f), CornerRadius(15f, 12f), CornerRadius(15f, 12f))) }
    p.fill(shape, Brush.linearGradient(0f to pal.bodyLight, 0.55f to pal.bodyMid, 1f to pal.bodyDark, start = Offset(-26f, -80f), end = Offset(30f, -8f)))
    p.clip(shape) {
        p.fill(oval(14f, -24f, 30f, 24f), Brush.radialGradient(0f to pal.clay.copy(alpha = 0.42f), 1f to pal.clay.copy(alpha = 0f), center = Offset(14f, -24f), radius = 30f))
        p.fill(oval(-16f, -66f, 17f, 20f), Brush.radialGradient(0f to Color.White.copy(alpha = if (pal.dark) 0.12f else 0.4f), 1f to Color.Transparent, center = Offset(-16f, -66f), radius = 20f))
        p.strokes(strokeField(11L, 26, -31f, -86f, 31f, -8f, 6f..14f, -35f, 20f, 1.2f..2.6f, 0.08f..0.2f), pal.olive, pal.cream)
        p.dots(dotField(12L, 70, -31f, -86f, 31f, -8f), pal.olive, pal.cream)
        p.line(path { moveTo(-31f, -41f); quadraticTo(0f, -35f, 31f, -41f) }, pal.olive, 1.1f, 0.2f)
        p.line(path { moveTo(-27f, -80f); quadraticTo(-30f, -50f, -24f -3f, -20f) }, pal.cream, 1.6f, if (pal.dark) 0.18f else 0.55f)
    }
    p.ds.drawCircle(pal.coral.copy(alpha = 0.35f), 3.6f, Offset(-22f, -50f))
    p.ds.drawCircle(pal.coral.copy(alpha = 0.35f), 3.6f, Offset(22f, -50f))
    p.leafAt(0f, -24f, -90f + 20f, 10f, 3.2f, pal.leafDeep, vein = false, a = 0.55f)
}

private fun arms(p: Painter, pose: MascotPose) {
    val (l, r) = armsOf(pose)
    val col = lerp(p.pal.bodyMid, p.pal.bodyDark, 0.55f)
    for ((hand, side) in listOf(l to -1f, r to 1f)) {
        val sx = side * 27f
        val sy = -46f
        p.line(path { moveTo(sx, sy); quadraticTo((sx + hand.x) / 2f + side * 4f, (sy + hand.y) / 2f + 7f, hand.x, hand.y) }, col, 10f)
        p.line(path { moveTo(sx, sy - 2f); quadraticTo((sx + hand.x) / 2f + side * 4f, (sy + hand.y) / 2f + 5f, hand.x, hand.y - 2f) }, p.pal.bodyLight, 2.2f, 0.35f)
    }
}

private fun hands(p: Painter, pose: MascotPose) {
    val (l, r) = armsOf(pose)
    for (h in listOf(l, r)) {
        p.fill(oval(h.x, h.y, 5.8f), p.pal.hand)
        p.fill(oval(h.x - 1.6f, h.y - 1.8f, 2.2f), Color.White, 0.3f)
    }
}

private fun props(p: Painter, pose: MascotPose) {
    val pal = p.pal
    when (pose) {
        MascotPose.Waving -> listOf(-14f, 0f, 14f).forEachIndexed { i, a ->
            p.at(47f, -94f, 1f, a - 16f) { p.line(path { moveTo(0f, -4f - i % 2); lineTo(0f, -10f - i % 2) }, pal.olive, 1.4f, 0.55f) }
        }
        MascotPose.Sleeping, MascotPose.Blanket -> {
            if (pose == MascotPose.Blanket) blanket(p)
            listOf(Triple(30f, -96f, 5f), Triple(40f, -106f, 6.5f), Triple(52f, -119f, 8f)).forEachIndexed { i, (x, y, s) ->
                p.line(path { moveTo(x - s, y - s); lineTo(x + s, y - s); lineTo(x - s, y + s); lineTo(x + s, y + s) }, if (pal.dark) pal.lilac else pal.olive, 1.5f, 0.55f - i * 0.1f)
            }
        }
        MascotPose.Reading -> book(p)
        MascotPose.Coin -> coin(p, 46f, -82f)
        MascotPose.Tending -> tending(p)
        MascotPose.Listening -> {
            for ((i, r) in listOf(11f, 19f, 27f).withIndex()) {
                p.line(path { arcTo(androidx.compose.ui.geometry.Rect(-52f - r, -64f - r, -52f + r, -64f + r), 140f, 80f, true) }, if (pal.dark) pal.glow else pal.leafDeep, 1.8f, 0.6f - i * 0.17f)
            }
        }
        MascotPose.Thinking -> {
            p.ds.drawCircle(pal.warmWhite, 2f, Offset(36f, -92f), 0.9f)
            p.ds.drawCircle(pal.warmWhite, 3f, Offset(43f, -101f), 0.92f)
            p.cloud(58f, -116f, 30f)
            p.sparkle(58f, -117f, 5f, pal.sun)
        }
        MascotPose.Lantern -> p.lanternProp(46f, -46f, 0.8f)
        MascotPose.Lifting -> dumbbell(p, 45f, -72f)
        MascotPose.Walking -> {
            p.fill(oval(-40f, -3f, 6f, 3f), pal.cream, 0.4f)
            p.fill(oval(-48f, -6f, 4f, 2.4f), pal.cream, 0.28f)
        }
        else -> Unit
    }
}

private fun book(p: Painter) {
    val pal = p.pal
    p.fill(path { moveTo(-24f, -20f); lineTo(24f, -20f); lineTo(24f, -45f); lineTo(0f, -41f); lineTo(-24f, -45f); close() }, pal.coral)
    for (side in listOf(-1f, 1f)) {
        p.fill(path { moveTo(0f, -22f); quadraticTo(side * 11f, -25f, side * 21f, -23f); lineTo(side * 21f, -44f); quadraticTo(side * 11f, -45f, 0f, -41f); close() }, pal.cream)
        repeat(4) { p.line(path { moveTo(side * 4f, -26f - it * 4.2f); lineTo(side * 17f, -27f - it * 4.2f) }, pal.olive, 0.8f, 0.35f) }
    }
    p.line(path { moveTo(0f, -22f); lineTo(0f, -41f) }, pal.olive, 0.8f, 0.4f)
    p.leafAt(12f, -43f, -60f, 8f, 3f, pal.leaf)
}

private fun coin(p: Painter, x: Float, y: Float) {
    val pal = p.pal
    p.glow(x, y, 20f, pal.glow, 0.7f)
    p.fill(oval(x, y, 11f), Brush.radialGradient(0f to pal.glow, 1f to pal.sun, center = Offset(x - 3f, y - 4f), radius = 16f))
    p.line(path { addOval(androidx.compose.ui.geometry.Rect(x - 8f, y - 8f, x + 8f, y + 8f)) }, pal.orange, 0.9f, 0.7f)
    p.leafAt(x - 3f, y + 4f, -50f, 8.5f, 3.2f, pal.leafDeep, vein = false, a = 0.8f)
    p.sparkle(x + 17f, y - 10f, 4.2f, pal.sun)
    p.sparkle(x - 15f, y - 14f, 3f, pal.glow)
}

private fun tending(p: Painter) {
    val pal = p.pal
    p.fill(oval(62f, -2f, 14f, 2.8f), pal.shade)
    p.fill(path { moveTo(50f, -18f); lineTo(74f, -18f); lineTo(70f, -2f); quadraticTo(62f, 1f, 54f, -2f); close() }, vgrad(-18f, 0f, pal.coral, pal.clay))
    p.fill(rrect(48f, -22f, 76f, -16f, 3f), pal.coral)
    p.fill(oval(62f, -21f, 11f, 2f), pal.olive, 0.9f)
    p.sprout(62f, -21f, 17f)
    p.fill(rrect(40f, -52f, 56f, -40f, 3.5f), vgrad(-52f, -40f, pal.sky, lerp(pal.sky, pal.lilac, 0.4f)))
    p.line(path { moveTo(55f, -48f); lineTo(65f, -53f) }, pal.sky, 2.6f)
    repeat(3) { p.fill(oval(64f + it * 0.6f, -44f + it * 6.5f, 1.2f, 1.9f), pal.sky, 0.9f) }
}

private fun dumbbell(p: Painter, x: Float, y: Float) = p.at(x, y, 1f, -14f) {
    val pal = p.pal
    p.fill(rrect(-14f, -1.6f, 14f, 1.6f, 1.6f), pal.olive)
    for (s in listOf(-1f, 1f)) {
        p.fill(rrect(if (s < 0) -17f else 9f, -6f, if (s < 0) -9f else 17f, 6f, 3f), vgrad(-6f, 6f, pal.orange, pal.coral))
        p.fill(rrect(if (s < 0) -20f else 17f, -4f, if (s < 0) -17f else 20f, 4f, 1.5f), pal.coral)
    }
}

private fun blanket(p: Painter) {
    val pal = p.pal
    val shape = path {
        moveTo(-39f, -4f); lineTo(-39f, -40f)
        quadraticTo(-30f, -50f, -20f, -42f); quadraticTo(-8f, -50f, 0f, -43f)
        quadraticTo(10f, -50f, 20f, -42f); quadraticTo(30f, -50f, 39f, -40f)
        lineTo(39f, -4f); quadraticTo(0f, 1f, -39f, -4f); close()
    }
    p.fill(shape, vgrad(-50f, 0f, lerp(pal.pink, pal.warmWhite, 0.2f), pal.pink))
    p.clip(shape) {
        for ((i, c) in listOf(pal.sun, pal.lilac, pal.sun, pal.lilac).withIndex()) p.fill(rrect(-40f + i * 22f, -52f, -32f + i * 22f, 2f, 0f), c, 0.55f)
        p.strokes(strokeField(31L, 14, -39f, -50f, 39f, 0f, 5f..10f, -20f, 30f, 1f..2f, 0.1f..0.2f), pal.cream, pal.cream)
    }
    p.line(path { moveTo(-37f, -37f); quadraticTo(-30f, -46f, -20f, -39f); quadraticTo(-8f, -46f, 0f, -40f); quadraticTo(10f, -46f, 20f, -39f); quadraticTo(30f, -46f, 37f, -37f) }, pal.warmWhite, 1.2f, 0.7f)
}

private fun confetti(p: Painter) {
    val r = Random(41)
    val cols = listOf(p.pal.orange, p.pal.pink, p.pal.sun, p.pal.lilac, p.pal.leaf, p.pal.coral)
    repeat(16) {
        val a = r.nextFloat() * 2f * PI.toFloat()
        val d = 44f + r.nextFloat() * 34f
        val x = kotlin.math.cos(a) * d * 1.1f
        val y = -60f + kotlin.math.sin(a) * d * 0.9f
        if (y < -4f) p.leafAt(x, y, r.nextFloat() * 360f, 6f + r.nextFloat() * 4f, 2.4f, cols[it % cols.size], vein = false, a = 0.95f)
    }
}

/**
 * The moving part of the mascot, drawn each frame above the cached scene: eyes (with [blink] 0 open to 1 shut) and
 * the antenna leaf swaying by [sway] in -1..1. Origin is the same as [drawMascotBody].
 */
internal fun DrawScope.drawMascotLive(pal: IllusPalette, pose: MascotPose, flip: Boolean, blink: Float, sway: Float) {
    val look = lookOf(pose)
    withTransform({
        if (flip) scale(-1f, 1f, Offset.Zero)
        if (pose == MascotPose.Walking) rotate(3f, Offset.Zero)
    }) {
        for (side in Eyes) {
            val cx = side * EyeX + look.dx
            val cy = EyeY + look.dy
            if (look.arc != 0) {
                val up = look.arc > 0
                drawPath(
                    path { moveTo(cx - 6f, cy + if (up) 2f else -1f); quadraticTo(cx, cy + if (up) -7f else 7f, cx + 6f, cy + if (up) 2f else -1f) },
                    pal.eye, 1f, Stroke(2.8f, cap = StrokeCap.Round),
                )
            } else {
                val ry = look.ry * (1f - 0.88f * blink)
                rotateAround(look.tilt * side, cx, cy) {
                    drawPath(oval(cx, cy, 5.2f, ry), pal.eye)
                    if (blink < 0.5f) drawCircle(Color.White, 1.5f, Offset(cx - 1.4f, cy - ry * 0.45f), 0.85f)
                }
            }
        }
        rotateAround(sway * 6f + droopOf(pose), 0f, -84f) {
            drawPath(path { moveTo(0f, -84f); quadraticTo(-1.5f, -96f, 4f, -106f) }, pal.olive, 1f, Stroke(2.6f, cap = StrokeCap.Round))
            drawPath(leafPath(1f, -94f, -150f, 12f, 4.4f), pal.leafLight)
            drawPath(leafPath(4f, -106f, -62f, 17f, 6.2f), Brush.linearGradient(0f to pal.leafLight, 1f to pal.leaf, start = Offset(4f, -106f), end = Offset(12f, -122f)))
            drawPath(path { moveTo(4f, -106f); lineTo(11.5f, -120f) }, pal.leafDeep, 1f, Stroke(0.8f, cap = StrokeCap.Round))
        }
    }
}
