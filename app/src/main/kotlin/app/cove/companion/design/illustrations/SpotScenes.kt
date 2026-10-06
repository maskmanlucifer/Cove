package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

private const val W = 240f
private const val H = 180f

private fun ArtBuilder.sprig(bx: Float, by: Float, height: Float, lean: Float = 0f, scale: Float = 1f) {
    line(path { moveTo(bx, by); quadraticTo(bx + lean * 0.3f, by - height * 0.5f, bx + lean, by - height) }, 1.5f, 0.6f)
    fill(leaf(bx + lean * 0.35f, by - height * 0.45f, -28f, 22f * scale, 7f * scale), pal.sage)
    fill(leaf(bx + lean * 0.6f, by - height * 0.7f, -152f, 20f * scale, 6.5f * scale), pal.sage)
    fill(leaf(bx + lean, by - height, -86f, 18f * scale, 6.5f * scale), pal.sage)
}

private fun Path.shifted(dx: Float, dy: Float) = Path().also { it.addPath(this, Offset(dx, dy)) }

internal fun todosArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 92f, 94f)
    shadow(120f, 152f, 96f)
    fill(rrect(36f, 133f, 204f, 145f, 6f), p.paper)
    line(path { moveTo(44f, 133f); lineTo(196f, 133f) }, 1.5f, 0.45f)
    fill(rrect(50f, 145f, 58f, 156f, 2f), p.paperShade)
    fill(rrect(182f, 145f, 190f, 156f, 2f), p.paperShade)
    val book = rrect(60f, 84f, 126f, 133f, 8f)
    fill(book, p.paper)
    fill(rrect(60f, 84f, 72f, 133f, 6f), p.lilac)
    line(book, 1.5f, 0.55f)
    for ((i, y) in listOf(99f, 111f, 123f).withIndex()) {
        fill(oval(85f, y, 4.2f), if (i == 0) p.peach else p.paperShade)
        line(oval(85f, y, 4.2f), 1.3f, 0.45f)
        line(path { moveTo(95f, y); lineTo(if (i == 1) 112f else 116f, y) }, 1.5f, 0.28f)
    }
    line(path { moveTo(82.5f, 99f); lineTo(84.5f, 101f); lineTo(88f, 97f) }, 1.4f, 0.8f)
    move(ax = 1.6f, py = 106f, px = 168f, grow = 0.012f) {
        for ((a, l) in listOf(-112f to 30f, -76f to 42f, -48f to 30f, -150f to 24f, -26f to 22f)) {
            fill(leaf(168f, 106f, a, l, 9f), p.sage)
        }
    }
    fill(rrect(150f, 107f, 186f, 133f, 9f), p.peach)
    fill(rrect(147f, 101f, 189f, 112f, 5f), p.peach)
    fill(rrect(147f, 101f, 189f, 112f, 5f), p.paper, 0.35f)
    line(rrect(150f, 112f, 186f, 133f, 9f), 1.4f, 0.4f)
    build(W, H)
}

internal fun scheduleArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 90f, 92f)
    shadow(118f, 152f, 66f)
    val card = rrect(72f, 40f, 164f, 142f, 16f)
    fill(card.shifted(0f, 3f), p.lilac, 0.35f)
    fill(card, p.paper)
    fill(path { moveTo(72f, 66f); lineTo(72f, 56f); quadraticTo(72f, 40f, 88f, 40f); lineTo(148f, 40f); quadraticTo(164f, 40f, 164f, 56f); lineTo(164f, 66f); close() }, p.lilac)
    line(card, 1.5f, 0.55f)
    for (x in listOf(98f, 138f)) line(path { moveTo(x, 31f); lineTo(x, 46f) }, 2.6f, 0.7f)
    for (r in 0..2) for (c in 0..2) {
        val cx = 94f + c * 24f
        val cy = 86f + r * 22f
        if (r == 1 && c == 1) { fill(oval(cx, cy, 10f), p.peach); line(oval(cx, cy, 10f), 1.4f, 0.5f) }
        else fill(oval(cx, cy, 3.6f), p.faint)
    }
    move(ax = 1.8f) { sprig(168f, 146f, 44f, 8f, 1.1f) }
    build(W, H)
}

internal fun moneyArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(116f, 92f, 92f)
    shadow(116f, 152f, 82f)
    fill(rrect(76f, 70f, 140f, 142f, 22f), p.paper, 0.8f)
    for (i in 0..3) {
        val y = 134f - i * 7f
        fill(oval(108f, y, 22f, 6.5f), p.peach)
        line(oval(108f, y, 22f, 6.5f), 1.2f, 0.4f)
    }
    line(rrect(76f, 70f, 140f, 142f, 22f), 1.5f, 0.55f)
    fill(rrect(82f, 58f, 134f, 72f, 7f), p.lilac)
    line(rrect(82f, 58f, 134f, 72f, 7f), 1.5f, 0.55f)
    line(path { moveTo(98f, 65f); lineTo(118f, 65f) }, 1.5f, 0.5f)
    move(ay = 4f, shift = 0.25f) {
        fill(oval(108f, 36f, 10f), p.peach)
        line(oval(108f, 36f, 10f), 1.4f, 0.55f)
        line(oval(108f, 36f, 5.5f), 1.2f, 0.35f)
    }
    for (i in 0..1) {
        fill(oval(178f, 142f - i * 6f, 16f, 5f), p.peach)
        line(oval(178f, 142f - i * 6f, 16f, 5f), 1.2f, 0.4f)
    }
    move(ax = 1.5f) { sprig(178f, 130f, 28f, 3f, 0.9f) }
    build(W, H)
}

internal fun journalArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 86f, 94f)
    val win = rrect(118f, 24f, 198f, 98f, 14f)
    fill(win, vertical(24f, 98f, p.sky.copy(alpha = 0.75f), p.base))
    fill(oval(178f, 48f, 8f), p.peach)
    fill(oval(178f, 48f, 20f), radial(178f, 48f, 20f, p.glow, 0.7f))
    move(ax = 3f) { fill(cloud(146f, 78f, 40f), p.paper, 0.85f) }
    line(win, 1.5f, 0.55f)
    line(path { moveTo(158f, 24f); lineTo(158f, 98f); moveTo(118f, 61f); lineTo(198f, 61f) }, 1.4f, 0.4f)
    shadow(120f, 152f, 82f)
    val left = path {
        moveTo(120f, 146f); cubicTo(100f, 138f, 72f, 138f, 48f, 145f); lineTo(48f, 110f)
        cubicTo(72f, 103f, 100f, 103f, 120f, 111f); close()
    }
    val right = path {
        moveTo(120f, 146f); cubicTo(140f, 138f, 168f, 138f, 192f, 145f); lineTo(192f, 110f)
        cubicTo(168f, 103f, 140f, 103f, 120f, 111f); close()
    }
    fill(left.shifted(0f, 5f), p.lilac); fill(right.shifted(0f, 5f), p.lilac)
    fill(left, p.paper); fill(right, p.paper)
    line(left, 1.5f, 0.55f); line(right, 1.5f, 0.55f)
    for (i in 0..2) {
        val y = 118f + i * 8f
        line(path { moveTo(60f, y + 2f); cubicTo(78f, y - 3f, 96f, y - 3f, 110f, y + 2f) }, 1.4f, 0.25f)
        if (i < 2) line(path { moveTo(130f, y + 2f); cubicTo(144f, y - 3f, 162f, y - 3f, 180f, y + 2f) }, 1.4f, 0.25f)
    }
    move(ay = 1.8f, ax = 1.2f) {
        fill(leaf(150f, 136f, -24f, 48f, 7.5f), p.lilac)
        line(path { moveTo(148f, 137f); lineTo(196f, 118f) }, 1.4f, 0.7f)
    }
    build(W, H)
}

internal fun habitsArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(124f, 96f, 96f)
    move(ay = 3f, grow = 0.03f, px = 198f, py = 52f) {
        fill(oval(198f, 52f, 34f), radial(198f, 52f, 34f, p.glow, 0.75f))
        fill(oval(198f, 52f, 13f), p.peach)
    }
    val stones = listOf(
        floatArrayOf(68f, 148f, 31f, 10f), floatArrayOf(108f, 128f, 25f, 8f), floatArrayOf(140f, 111f, 20f, 6.2f),
        floatArrayOf(164f, 97f, 15f, 4.8f), floatArrayOf(181f, 86f, 11f, 3.5f),
    )
    val tints = listOf(p.peach, p.lilac, p.sky, p.peach, p.lilac)
    shadow(120f, 158f, 100f)
    for ((i, s) in stones.withIndex()) {
        fill(oval(s[0], s[1] + 4f, s[2], s[3]), p.shadow)
        fill(oval(s[0], s[1] + 2.5f, s[2], s[3]), tints[i], 0.65f)
        fill(oval(s[0], s[1], s[2], s[3]), tints[i])
        fill(oval(s[0] - s[2] * 0.18f, s[1] - s[3] * 0.2f, s[2] * 0.6f, s[3] * 0.5f), p.paper, 0.35f)
        line(oval(s[0], s[1], s[2], s[3]), 1.3f, 0.35f)
    }
    move(ax = 1.2f) { sprig(181f, 86f, 18f, 3f, 0.7f) }
    fill(leaf(36f, 150f, -60f, 12f, 4f), p.sage); fill(leaf(36f, 150f, -120f, 12f, 4f), p.sage)
    build(W, H)
}

internal fun alarmsArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 90f, 90f)
    shadow(120f, 148f, 90f)
    fill(rrect(32f, 136f, 208f, 148f, 6f), p.paper)
    line(path { moveTo(40f, 136f); lineTo(200f, 136f) }, 1.5f, 0.45f)
    move(grow = 0.07f, px = 72f, py = 98f) { fill(oval(72f, 98f, 60f), radial(72f, 98f, 60f, p.glow, if (p.dark) 0.5f else 0.9f)) }
    line(path { moveTo(72f, 134f); lineTo(72f, 96f) }, 2f, 0.7f)
    fill(path { moveTo(52f, 96f); lineTo(92f, 96f); lineTo(83f, 62f); lineTo(61f, 62f); close() }, p.peach)
    fill(path { moveTo(52f, 96f); lineTo(92f, 96f); lineTo(88f, 82f); lineTo(56f, 82f); close() }, p.paper, 0.25f)
    line(path { moveTo(52f, 96f); lineTo(92f, 96f); lineTo(83f, 62f); lineTo(61f, 62f); close() }, 1.4f, 0.5f)
    fill(oval(72f, 135f, 17f, 4f), p.lilac)
    fill(oval(136f, 94f, 8f), p.sky); fill(oval(172f, 94f, 8f), p.sky)
    line(path { moveTo(142f, 91f); lineTo(166f, 91f) }, 1.4f, 0.5f)
    fill(oval(154f, 114f, 25f), p.paper)
    line(oval(154f, 114f, 25f), 1.5f, 0.6f)
    line(path { moveTo(142f, 138f); lineTo(138f, 143f); moveTo(166f, 138f); lineTo(170f, 143f) }, 1.5f, 0.6f)
    for (a in 0..3) {
        val dx = listOf(0f, 18f, 0f, -18f)[a]; val dy = listOf(-18f, 0f, 18f, 0f)[a]
        line(path { moveTo(154f + dx, 114f + dy); lineTo(154f + dx * 0.88f, 114f + dy * 0.88f) }, 1.4f, 0.4f)
    }
    line(path { moveTo(154f, 114f); lineTo(154f, 101f); moveTo(154f, 114f); lineTo(163f, 119f) }, 1.7f, 0.8f)
    fill(oval(154f, 114f, 2f), p.line)
    build(W, H)
}

internal fun trainingArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 90f, 92f)
    move(ay = 3f, grow = 0.03f, px = 176f, py = 52f) {
        fill(oval(176f, 52f, 38f), radial(176f, 52f, 38f, p.glow, 0.75f))
        fill(oval(176f, 52f, 15f), p.peach)
    }
    shadow(124f, 154f, 92f)
    val top = path {
        moveTo(66f, 124f); lineTo(190f, 124f); quadraticTo(200f, 124f, 204f, 134f); lineTo(208f, 147f)
        quadraticTo(209f, 150f, 205f, 150f); lineTo(48f, 150f); quadraticTo(44f, 150f, 45f, 147f); lineTo(52f, 134f)
        quadraticTo(55f, 124f, 66f, 124f); close()
    }
    fill(top.shifted(0f, 5f), p.lilac, 0.5f)
    fill(top, p.lilac)
    line(top, 1.5f, 0.5f)
    fill(oval(128f, 146f, 52f, 4.5f), p.shadow)
    line(path { moveTo(104f, 129f); lineTo(152f, 129f) }, 3f, 0.8f)
    fill(rrect(118f, 125f, 138f, 133f, 4f), p.sky)
    for (s in listOf(1, -1)) {
        val cx = 128f
        fun x(d: Float) = cx + s * d
        fun r(a: Float, b: Float, t: Float, bt: Float, rad: Float) = rrect(minOf(x(a), x(b)), t, maxOf(x(a), x(b)), bt, rad)
        fill(r(34f, 44f, 108f, 148f, 5f), p.peach); line(r(34f, 44f, 108f, 148f, 5f), 1.5f, 0.5f)
        fill(r(44f, 52f, 114f, 142f, 4f), p.paper); line(r(44f, 52f, 114f, 142f, 4f), 1.5f, 0.5f)
    }
    build(W, H)
}

internal fun voiceArt(p: ScenePalette) = ArtBuilder(p).run {
    backdrop(120f, 90f, 90f, strength = if (p.dark) 0.35f else 0.55f)
    move(grow = 0.04f, px = 120f, py = 90f) {
        line(oval(120f, 90f, 80f), 1.3f, 0.12f)
        line(oval(120f, 90f, 64f), 1.3f, 0.18f)
        line(oval(120f, 90f, 49f), 1.3f, 0.26f)
    }
    fill(oval(120f, 90f, 80f), radial(120f, 90f, 80f, p.sky, 0.28f))
    fill(oval(120f, 90f, 34f), p.base)
    fill(oval(120f, 90f, 34f), radial(122f, 104f, 36f, p.sky))
    fill(oval(120f, 90f, 34f), radial(134f, 82f, 30f, p.lilac))
    fill(oval(120f, 90f, 34f), radial(108f, 80f, 28f, p.peach))
    fill(oval(120f, 90f, 34f), p.paper, 0.1f)
    val heights = listOf(5f, 11f, 19f, 27f, 19f, 11f, 5f)
    for ((i, h) in heights.withIndex()) {
        val x = 102f + i * 6f
        line(path { moveTo(x, 90f - h / 2); lineTo(x, 90f + h / 2) }, 2.2f, 0.62f)
    }
    for ((x, y) in listOf(48f to 54f, 196f to 62f, 188f to 142f, 58f to 138f)) fill(oval(x, y, 2.6f), p.line, 0.3f)
    build(W, H)
}
