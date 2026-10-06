package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

private const val W = 240f
private const val H = 180f

private fun ArtBuilder.sprig(bx: Float, by: Float, height: Float, lean: Float = 0f, scale: Float = 1f) {
    line(path { moveTo(bx, by); quadraticTo(bx + lean * 0.3f, by - height * 0.5f, bx + lean, by - height) }, 1.5f, 0.6f)
    fill(leaf(bx + lean * 0.35f, by - height * 0.45f, -28f, 22f * scale, 7f * scale), pal.sage)
    fill(leaf(bx + lean * 0.6f, by - height * 0.7f, -152f, 20f * scale, 6.5f * scale), pal.sage)
    fill(leaf(bx + lean, by - height, -86f, 18f * scale, 6.5f * scale), pal.sage)
}

private fun Path.shifted(dx: Float, dy: Float) = Path().also { it.addPath(this, Offset(dx, dy)) }

internal fun messagesArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(112f, 96f, 92f)
    shadow(106f, 152f, 70f)
    fill(rrect(72f, 72f, 134f, 104f, 5f), p.base)
    line(rrect(72f, 72f, 134f, 104f, 5f), 1.4f, 0.4f)
    line(path { moveTo(82f, 82f); lineTo(118f, 82f); moveTo(82f, 90f); lineTo(106f, 90f) }, 1.4f, 0.28f)
    val body = rrect(56f, 92f, 152f, 150f, 11f)
    fill(body.shifted(0f, 3f), p.lilac, 0.35f)
    fill(body, p.paper)
    fill(path { moveTo(56f, 104f); quadraticTo(56f, 92f, 68f, 92f); lineTo(140f, 92f); quadraticTo(152f, 92f, 152f, 104f); lineTo(104f, 128f); close() }, p.paperShade)
    line(body, 1.5f, 0.55f)
    line(path { moveTo(58f, 100f); lineTo(104f, 126f); lineTo(150f, 100f) }, 1.5f, 0.55f)
    line(path { moveTo(60f, 146f); lineTo(88f, 120f); moveTo(148f, 146f); lineTo(120f, 120f) }, 1.3f, 0.3f)
    line(path { moveTo(150f, 86f); cubicTo(164f, 72f, 160f, 56f, 176f, 52f) }, 1.5f, 0.45f, dashed = true)
    move(ax = 3f, ay = -3f) {
        val tri = path { moveTo(168f, 58f); lineTo(214f, 34f); lineTo(190f, 74f); lineTo(186f, 60f); close() }
        fill(tri, p.paper)
        fill(path { moveTo(186f, 60f); lineTo(214f, 34f); lineTo(190f, 74f); close() }, p.lilac)
        line(tri, 1.5f, 0.6f)
        line(path { moveTo(214f, 34f); lineTo(186f, 60f) }, 1.4f, 0.5f)
    }
    build(W, H)
}

internal fun syncedArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 96f, 94f)
    move(ax = -5f, shift = 0.5f) { fill(cloud(58f, 70f, 40f), p.lilac, 0.5f) }
    move(ax = 6f) { fill(cloud(190f, 62f, 46f), p.paper, 0.85f) }
    shadow(120f, 152f, 66f)
    val c = cloud(120f, 140f, 128f)
    fill(c.shifted(0f, 3f), p.lilac, 0.4f)
    fill(c, p.paper)
    fill(oval(120f, 112f, 17f), p.sky)
    line(oval(120f, 112f, 17f), 1.5f, 0.45f)
    line(path { moveTo(112f, 112f); lineTo(118f, 118f); lineTo(129f, 105f) }, 1.9f, 0.8f)
    build(W, H)
}

internal fun offlineArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 88f, 88f, strength = if (p.dark) 0.35f else 0.6f)
    move(ay = 2f) {
        shadow(120f, 112f, 50f, 4f)
        val c = cloud(120f, 100f, 112f)
        fill(c.shifted(0f, 3f), p.lilac, 0.35f)
        fill(c, p.paper)
    }
    line(path { moveTo(112f, 104f); cubicTo(112f, 126f, 70f, 118f, 84f, 146f) }, 1.7f, 0.6f)
    shadow(122f, 158f, 76f)
    fill(rrect(82f, 138f, 108f, 154f, 5f), p.peach)
    line(rrect(82f, 138f, 108f, 154f, 5f), 1.5f, 0.55f)
    line(path { moveTo(108f, 143f); lineTo(118f, 143f); moveTo(108f, 149f); lineTo(118f, 149f) }, 1.7f, 0.65f)
    fill(rrect(146f, 132f, 184f, 158f, 9f), p.paper)
    line(rrect(146f, 132f, 184f, 158f, 9f), 1.5f, 0.55f)
    line(path { moveTo(154f, 141f); lineTo(162f, 141f); moveTo(154f, 149f); lineTo(162f, 149f) }, 1.7f, 0.5f)
    build(W, H)
}

private fun ringSegment(cx: Float, cy: Float, ro: Float, ri: Float, startDeg: Float, sweepDeg: Float) = path {
    arcTo(Rect(cx - ro, cy - ro, cx + ro, cy + ro), startDeg, sweepDeg, true)
    arcTo(Rect(cx - ri, cy - ri, cx + ri, cy + ri), startDeg + sweepDeg, -sweepDeg, false)
    close()
}

internal fun helpArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 92f, 92f)
    move(ay = 2.5f) {
        shadow(120f, 142f, 44f, 5f)
        val ring = path { fillType = PathFillType.EvenOdd; addOval(Rect(78f, 48f, 162f, 132f)); addOval(Rect(104f, 74f, 136f, 106f)) }
        fill(ring, p.paper)
        for (a in listOf(-45f, 45f, 135f, 225f)) fill(ringSegment(120f, 90f, 42f, 16f, a - 17f, 34f), p.peach)
        line(oval(120f, 90f, 42f), 1.5f, 0.55f)
        line(oval(120f, 90f, 16f), 1.5f, 0.55f)
        line(oval(120f, 90f, 49f), 1.3f, 0.3f, dashed = true)
    }
    move(ax = 5f, shift = 0.3f) {
        for ((y, a) in listOf(150f to 0.55f, 162f to 0.35f)) {
            line(path { moveTo(52f, y); cubicTo(72f, y - 7f, 88f, y + 7f, 108f, y); cubicTo(128f, y - 7f, 144f, y + 7f, 164f, y); cubicTo(176f, y - 5f, 184f, y + 4f, 190f, y) }, 2f, a, p.sky)
        }
    }
    build(W, H)
}

internal fun lanternArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 92f, 92f)
    move(grow = 0.06f, px = 120f, py = 98f) { fill(oval(120f, 98f, 74f), radial(120f, 98f, 74f, p.glow, if (p.dark) 0.45f else 0.85f)) }
    shadow(120f, 148f, 46f)
    line(path { moveTo(100f, 54f); cubicTo(100f, 26f, 140f, 26f, 140f, 54f) }, 1.8f, 0.65f)
    val body = rrect(96f, 62f, 144f, 128f, 12f)
    fill(body, Brush.radialGradient(0f to p.glow, 0.55f to p.peach.copy(alpha = 0.7f), 1f to p.paper, center = Offset(120f, 100f), radius = 44f))
    line(path { moveTo(112f, 64f); lineTo(112f, 126f); moveTo(128f, 64f); lineTo(128f, 126f) }, 1.3f, 0.3f)
    line(body, 1.5f, 0.55f)
    val cap = path { moveTo(90f, 64f); quadraticTo(120f, 38f, 150f, 64f); close() }
    fill(cap, p.lilac)
    line(cap, 1.5f, 0.55f)
    fill(rrect(92f, 126f, 148f, 138f, 6f), p.lilac)
    line(rrect(92f, 126f, 148f, 138f, 6f), 1.5f, 0.55f)
    move(ay = -1.2f, grow = 0.05f, px = 120f, py = 112f) {
        fill(path { moveTo(120f, 114f); cubicTo(109f, 106f, 115f, 98f, 120f, 88f); cubicTo(125f, 98f, 131f, 106f, 120f, 114f) }, if (p.dark) p.moon else p.paper, 0.95f)
    }
    build(W, H)
}

internal fun secureArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 92f, 92f)
    shadow(118f, 150f, 58f)
    line(path { moveTo(98f, 86f); lineTo(98f, 66f); cubicTo(98f, 30f, 142f, 30f, 142f, 66f); lineTo(142f, 86f) }, 4.2f, 0.7f)
    val body = rrect(78f, 84f, 162f, 144f, 18f)
    fill(body, p.lilac)
    fill(body, radial(100f, 100f, 60f, p.peach, 0.7f))
    line(body, 1.5f, 0.5f)
    fill(oval(120f, 108f, 6.5f), p.line, 0.75f)
    fill(rrect(117.5f, 110f, 122.5f, 126f, 2.5f), p.line, 0.75f)
    move(ax = 2f) { sprig(160f, 100f, 50f, 14f, 1.1f) }
    build(W, H)
}

private fun sparkle(cx: Float, cy: Float, r: Float) = path {
    moveTo(cx, cy - r); quadraticTo(cx, cy, cx + r, cy); quadraticTo(cx, cy, cx, cy + r); quadraticTo(cx, cy, cx - r, cy); quadraticTo(cx, cy, cx, cy - r); close()
}

internal fun clearedArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 94f, 92f)
    shadow(120f, 150f, 70f)
    val front = rrect(74f, 92f, 166f, 148f, 9f)
    fill(front, p.paper)
    fill(rrect(74f, 118f, 166f, 148f, 9f), p.paperShade, 0.6f)
    line(front, 1.5f, 0.55f)
    val lid = rrect(68f, 76f, 172f, 98f, 9f)
    fill(lid, p.lilac)
    line(lid, 1.5f, 0.55f)
    fill(rrect(112f, 76f, 128f, 148f, 3f), p.peach, 0.85f)
    line(path { moveTo(112f, 76f); lineTo(112f, 148f); moveTo(128f, 76f); lineTo(128f, 148f) }, 1.3f, 0.35f)
    move(ay = -3f, grow = 0.08f, px = 184f, py = 52f) { fill(sparkle(184f, 52f, 10f), p.glow); line(sparkle(184f, 52f, 10f), 1.3f, 0.4f) }
    move(ay = -2f, grow = 0.08f, px = 56f, py = 70f, shift = 0.4f) { fill(sparkle(56f, 70f, 6f), p.glow); line(sparkle(56f, 70f, 6f), 1.2f, 0.4f) }
    move(ax = 1.5f) { sprig(196f, 148f, 26f, 4f, 0.8f) }
    build(W, H)
}
