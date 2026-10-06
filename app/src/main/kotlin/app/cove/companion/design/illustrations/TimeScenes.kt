package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.lerp

private const val W = 360f
private const val H = 160f

private fun sky(top: Color, bottom: Color, y1: Float = H) = vertical(0f, y1, top, bottom)

private fun ArtBuilder.sky(top: Color, bottom: Color) = fill(rrect(0f, 0f, W, H, 0f), vertical(0f, H, top, bottom))

private fun hill(y0: Float, y1: Float, y2: Float, y3: Float, bump: Float = 14f) = path {
    moveTo(0f, y0)
    cubicTo(70f, y0 - bump, 130f, y1 - bump, 190f, y1)
    cubicTo(250f, y1 + bump * 0.6f, 310f, y3 - bump, W, y2)
    lineTo(W, H); lineTo(0f, H); close()
}

internal fun morningArt(p: ScenePalette) = ArtBuilder(p).run {
    sky(lerp(p.base, p.sky, 0.55f), lerp(p.base, p.peach, 0.7f))
    move(ay = -3f, grow = 0.03f, px = 258f, py = 96f) {
        fill(oval(258f, 96f, 96f), radial(258f, 96f, 96f, p.glow, if (p.dark) 0.45f else 0.75f))
        fill(oval(258f, 96f, 22f), if (p.dark) p.glow else lerp(p.peach, p.paper, 0.45f))
    }
    line(path { moveTo(64f, 46f); quadraticTo(69f, 40f, 74f, 46f); quadraticTo(79f, 40f, 84f, 46f) }, 1.4f, 0.45f)
    line(path { moveTo(98f, 34f); quadraticTo(102f, 29f, 106f, 34f); quadraticTo(110f, 29f, 114f, 34f) }, 1.3f, 0.35f)
    fill(hill(112f, 106f, 100f, 118f), lerp(p.lilac, p.base, 0.35f), 0.85f)
    fill(hill(132f, 128f, 130f, 140f, 10f), lerp(p.sky, p.lilac, 0.5f), 0.8f)
    fill(hill(146f, 142f, 142f, 146f, 6f), lerp(p.lilac, p.paper, if (p.dark) 0.1f else 0.6f))
    build(W, H)
}

internal fun afternoonArt(p: ScenePalette) = ArtBuilder(p).run {
    sky(lerp(p.base, p.sky, 0.85f), lerp(p.base, p.sky, 0.15f))
    move(grow = 0.025f, px = 276f, py = 52f) {
        fill(oval(276f, 52f, 70f), radial(276f, 52f, 70f, p.glow, 0.5f))
        fill(oval(276f, 52f, 24f), p.base)
        fill(oval(276f, 52f, 24f), radial(280f, 64f, 26f, p.sky))
        fill(oval(276f, 52f, 24f), radial(286f, 44f, 22f, p.lilac))
        fill(oval(276f, 52f, 24f), radial(266f, 44f, 20f, p.peach))
    }
    move(ax = 8f) { fill(cloud(96f, 74f, 100f), p.paper, 0.92f) }
    move(ax = -6f, shift = 0.4f) { fill(cloud(214f, 112f, 64f), p.paper, 0.8f) }
    move(ax = 5f, shift = 0.7f) { fill(cloud(332f, 40f, 40f), p.paper, 0.6f) }
    fill(hill(138f, 134f, 136f, 144f, 8f), p.lilac, 0.4f)
    fill(hill(152f, 148f, 148f, 152f, 5f), p.paper, 0.85f)
    build(W, H)
}

internal fun eveningArt(p: ScenePalette) = ArtBuilder(p).run {
    sky(lerp(p.lilac, p.base, if (p.dark) 0.2f else 0.15f), lerp(p.peach, p.base, 0.15f))
    move(ay = 2f, grow = 0.02f, px = 130f, py = 112f) {
        fill(oval(130f, 112f, 110f), radial(130f, 112f, 110f, p.glow, if (p.dark) 0.45f else 0.85f))
        fill(oval(130f, 112f, 24f), if (p.dark) p.glow else lerp(p.peach, p.paper, 0.4f))
    }
    for ((x, y, r) in listOf(Triple(60f, 26f, 1.6f), Triple(300f, 38f, 1.4f), Triple(214f, 18f, 1.8f), Triple(332f, 70f, 1.2f))) fill(oval(x, y, r), p.moon, 0.7f)
    move(ax = 6f) { fill(oval(250f, 62f, 44f, 3.5f), p.paper, 0.4f); fill(oval(236f, 71f, 26f, 2.8f), p.paper, 0.3f) }
    fill(hill(116f, 112f, 108f, 118f, 12f), lerp(p.lilac, p.night, if (p.dark) 0.5f else 0.28f), 0.9f)
    fill(hill(144f, 140f, 138f, 148f, 8f), lerp(p.lilac, p.night, if (p.dark) 0.75f else 0.5f), 0.95f)
    build(W, H)
}

internal fun nightArt(p: ScenePalette) = ArtBuilder(p).run {
    sky(p.night, p.nightHi)
    move(grow = 0.04f, px = 92f, py = 50f) { fill(oval(92f, 50f, 64f), radial(92f, 50f, 64f, p.moon, 0.22f)) }
    fill(Path.combine(PathOperation.Difference, oval(92f, 50f, 18f), oval(101f, 45f, 16f)), p.moon)
    val stars = listOf(
        floatArrayOf(150f, 30f, 1.7f, 0.9f), floatArrayOf(196f, 52f, 1.3f, 0.6f), floatArrayOf(238f, 22f, 1.8f, 0.9f),
        floatArrayOf(280f, 46f, 1.3f, 0.6f), floatArrayOf(322f, 24f, 1.6f, 0.8f), floatArrayOf(40f, 28f, 1.3f, 0.6f),
        floatArrayOf(52f, 74f, 1.1f, 0.5f), floatArrayOf(330f, 72f, 1.1f, 0.5f), floatArrayOf(214f, 78f, 1.1f, 0.5f),
    )
    for (s in stars) fill(oval(s[0], s[1], s[2]), p.moon, s[3])
    move(grow = 0.12f, px = 238f, py = 22f, shift = 0.3f) {
        line(path { moveTo(238f, 14f); lineTo(238f, 30f); moveTo(230f, 22f); lineTo(246f, 22f) }, 1.2f, 0.5f, p.moon)
    }
    fill(path { moveTo(0f, 100f); cubicTo(60f, 90f, 100f, 98f, 160f, 100f); cubicTo(230f, 104f, 290f, 90f, W, 98f); lineTo(W, H); lineTo(0f, H); close() }, lerp(p.night, p.nightHi, 0.25f))
    fill(rrect(0f, 100f, W, H, 0f), vertical(100f, H, lerp(p.nightHi, p.night, 0.15f), p.night))
    move(ax = 2.5f, shift = 0.2f) {
        for ((i, w) in listOf(26f, 20f, 14f, 8f).withIndex()) {
            val y = 112f + i * 11f
            line(path { moveTo(92f - w, y); lineTo(92f + w, y) }, 1.7f, 0.5f - i * 0.08f, p.moon)
        }
    }
    line(path { moveTo(190f, 124f); lineTo(262f, 124f); moveTo(230f, 138f); lineTo(300f, 138f); moveTo(30f, 140f); lineTo(70f, 140f) }, 1.2f, 0.12f, p.moon)
    build(W, H)
}
