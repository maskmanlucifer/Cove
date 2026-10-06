package app.cove.companion.design.illustrations

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

internal const val WideW = 360f
internal const val WideH = 200f

private val Garden = listOf(Bloom.Orange, Bloom.Yellow, Bloom.Lilac, Bloom.Pink, Bloom.White, Bloom.White, Bloom.Daisy)

/** Three rolling hill layers and the flower meadow in front; [haze] tints the far hills with the sky's colour. */
internal fun Painter.rollingMeadow(w: Float, h: Float, horizon: Float, haze: Color, flowers: Int = 26, bloomScale: Float = 1f, between: () -> Unit = {}) {
    val far = greens(0.1f, haze)
    hill(pts(0f, horizon, 70f, horizon - 14f, 150f, horizon - 4f, 240f, horizon - 18f, w, horizon - 6f), h, far.first, far.second, pal.leaf, pal.warmWhite, 40, pal.warmWhite, 1)
    val mid = greens(0.45f, haze)
    hill(pts(0f, horizon + 18f, 90f, horizon + 4f, 190f, horizon + 20f, 290f, horizon + 8f, w, horizon + 22f), h, mid.first, mid.second, pal.leafDeep, pal.leafLight, 110, pal.leafLight, 2)
    scatter(21L, 22, 6f, w - 6f, horizon + 16f, horizon + 40f, 2.2f, 3.6f, listOf(Bloom.Daisy, Bloom.Daisy, Bloom.Lilac, Bloom.Yellow, Bloom.Orange))
    val near = greens(0.9f, haze)
    hill(pts(0f, horizon + 44f, 110f, horizon + 32f, 220f, horizon + 46f, w, horizon + 34f), h, near.first, near.second, pal.leafDeep, pal.leaf, 150, pal.leafLight, 3)
    blades(0f, w, horizon + 56f, (w / 3f).toInt(), 6f, 13f, pal.grassDark, pal.leaf, 4)
    scatter(23L, flowers, 4f, w - 4f, horizon + 52f, h - 26f, 4.5f * bloomScale, 7.5f * bloomScale, Garden, stems = true, groundY = h + 6f)
    between()
    scatter(24L, (w / 24f).toInt(), 2f, w - 2f, h - 22f, h - 6f, 7f * bloomScale, 9f * bloomScale, Garden, stems = true, groundY = h + 8f)
    blades(0f, w, h + 1f, (w / 4f).toInt(), 6f, 16f, pal.leafDeep, pal.grassDark, 5, 4f, 1.9f)
}

private fun Painter.birds(vararg xy: Float) {
    for (i in 0 until xy.size / 3) {
        val x = xy[i * 3]; val y = xy[i * 3 + 1]; val s = xy[i * 3 + 2]
        line(path { moveTo(x - 5 * s, y); quadraticTo(x - 2.5f * s, y - 4 * s, x, y); quadraticTo(x + 2.5f * s, y - 4 * s, x + 5 * s, y) }, pal.ink, 1.1f, 0.5f)
    }
}

private fun Painter.starsAndSparkles(sd: Long, count: Int, w: Float, h: Float) {
    val r = Random(seed + sd)
    repeat(count) {
        val x = r.nextFloat() * w; val y = r.nextFloat() * h
        if (it % 3 == 0) sparkle(x, y, 2.5f + r.nextFloat() * 2.5f, pal.cream, 0.9f) else fill(oval(x, y, 0.7f + r.nextFloat() * 0.8f), pal.cream, 0.4f + r.nextFloat() * 0.5f)
    }
}

internal fun morningArt() = SceneArt(WideW, WideH, ArtShape.Wide, MascotSpot(MascotPose.Waving, 104f, 188f, 1.05f)) {
    val top = if (pal.dark) pal.nightTop else pal.mix(pal.lilacSoft, pal.sky, 0.28f)
    fill(rrect(0f, 0f, WideW, WideH, 0f), Brush.verticalGradient(0f to top, 0.45f to pal.pinkSoft, 0.8f to pal.sunSoft, 1f to pal.sunSoft, startY = 0f, endY = 150f))
    sun(268f, 118f, 22f)
    cloud(70f, 42f, 80f, a0 = 0.9f); cloud(200f, 26f, 54f, a0 = 0.7f); cloud(318f, 58f, 44f, a0 = 0.7f)
    birds(150f, 52f, 1f, 166f, 44f, 0.8f)
    rollingMeadow(WideW, WideH, 120f, pal.pinkSoft, between = { mascot() })
    glow(268f, 128f, 70f, pal.glow, 0.4f)
    butterfly(212f, 150f, 1f, pal.orange)
    grain(WideW, WideH)
}

internal fun afternoonArt() = SceneArt(WideW, WideH, ArtShape.Wide, MascotSpot(MascotPose.Walking, 232f, 190f, 1.05f)) {
    val top = if (pal.dark) pal.mix(pal.nightTop, pal.sky, 0.25f) else pal.mix(pal.sky, pal.warmWhite, 0.3f)
    val low = if (pal.dark) pal.mix(pal.nightTop, pal.sunSoft, 0.5f) else pal.mix(pal.sunSoft, pal.warmWhite, 0.4f)
    fill(rrect(0f, 0f, WideW, WideH, 0f), vgrad(0f, 140f, top, low))
    sun(86f, 46f, 20f)
    cloud(250f, 40f, 90f); cloud(160f, 78f, 50f, a0 = 0.8f); cloud(334f, 82f, 46f, a0 = 0.7f)
    birds(206f, 70f, 1f, 224f, 62f, 0.9f, 300f, 28f, 0.7f)
    rollingMeadow(WideW, WideH, 112f, pal.sunSoft, between = { mascot() })
    butterfly(150f, 150f, 1.1f, pal.pink); butterfly(60f, 168f, 0.9f, pal.lilac, 20f)
    grain(WideW, WideH)
}

internal fun eveningArt() = SceneArt(WideW, WideH, ArtShape.Wide, MascotSpot(MascotPose.Lantern, 240f, 190f, 1.05f)) {
    val top = if (pal.dark) pal.nightTop else pal.mix(pal.lilac, pal.lilacSoft, 0.35f)
    val mid = if (pal.dark) pal.mix(pal.nightLow, pal.coralSoft, 0.4f) else pal.mix(pal.pink, pal.coralSoft, 0.5f)
    fill(rrect(0f, 0f, WideW, WideH, 0f), Brush.verticalGradient(0f to top, 0.55f to mid, 1f to pal.mix(pal.coralSoft, pal.sunSoft, 0.5f), startY = 0f, endY = 140f))
    if (pal.dark) starsAndSparkles(2L, 12, WideW, 70f)
    sun(98f, 112f, 24f, rays = false)
    cloud(220f, 44f, 80f, pal.mix(pal.warmWhite, pal.pink, 0.4f), 0.75f); cloud(60f, 36f, 52f, pal.mix(pal.warmWhite, pal.coral, 0.3f), 0.65f)
    rollingMeadow(WideW, WideH, 116f, pal.pinkSoft, bloomScale = 0.95f, between = { mascot() })
    fill(rrect(0f, 0f, WideW, WideH, 0f), pal.mix(pal.coral, pal.lilac, 0.5f), if (pal.dark) 0.0f else 0.07f)
    fireflies(5L, 11, 10f, 112f, 350f, 190f)
    grain(WideW, WideH)
}

internal fun nightArt() = SceneArt(WideW, WideH, ArtShape.Wide, MascotSpot(MascotPose.Blanket, 112f, 190f, 1.05f)) {
    fill(rrect(0f, 0f, WideW, WideH, 0f), vgrad(0f, 150f, pal.nightTop, pal.nightLow))
    starsAndSparkles(3L, 40, WideW, 100f)
    moon(284f, 52f, 20f)
    cloud(78f, 38f, 56f, pal.mix(pal.nightLow, pal.warmWhite, 0.3f), 0.5f)
    rollingMeadow(WideW, WideH, 118f, pal.nightLow, flowers = 22, between = { mascot() })
    // pond with the moon in it
    val pond = oval(262f, 178f, 62f, 12f)
    fill(pond, vgrad(166f, 190f, pal.mix(pal.nightLow, pal.leafDeep, 0.35f), pal.nightTop))
    clip(pond) {
        glow(264f, 178f, 34f, pal.glow, 0.5f)
        fill(oval(264f, 178f, 6f, 2.6f), pal.cream, 0.8f)
        repeat(5) { line(path { moveTo(248f + it * 7f, 172f + it * 2.5f); lineTo(262f + it * 8f, 172.5f + it * 2.5f) }, pal.cream, 0.8f, 0.3f) }
    }
    line(path { addOval(androidx.compose.ui.geometry.Rect(200f, 166f, 324f, 190f)) }, pal.leafLight, 1.2f, 0.4f)
    blades(204f, 322f, 190f, 24, 5f, 11f, pal.leafDeep, pal.grassDark, 9)
    fireflies(6L, 9, 140f, 120f, 350f, 180f)
    grain(WideW, WideH)
}

/** The signature scene: the companion waving in a flower meadow. */
internal fun welcomeArt() = SceneArt(WideW, 300f, ArtShape.Wide, MascotSpot(MascotPose.Waving, 178f, 284f, 1.75f)) {
    val h = 300f
    val top = if (pal.dark) pal.nightTop else pal.mix(pal.lilacSoft, pal.sky, 0.3f)
    fill(rrect(0f, 0f, WideW, h, 0f), Brush.verticalGradient(0f to top, 0.5f to pal.pinkSoft, 0.8f to pal.sunSoft, startY = 0f, endY = 190f))
    sun(286f, 74f, 24f)
    cloud(70f, 56f, 96f); cloud(200f, 34f, 56f, a0 = 0.75f); cloud(326f, 128f, 56f, a0 = 0.7f)
    birds(130f, 78f, 1.1f, 150f, 68f, 0.9f)
    rollingMeadow(WideW, h, 170f, pal.pinkSoft, flowers = 40, bloomScale = 1.25f, between = { mascot() })
    // big botanical whites framing the scene
    for ((x, y, s) in listOf(Triple(28f, 252f, 26f), Triple(330f, 246f, 24f), Triple(60f, 288f, 19f), Triple(304f, 286f, 20f))) {
        line(path { moveTo(x, h + 4f); quadraticTo(x + 4f, (y + h) / 2f, x, y) }, pal.leafDeep, 2f)
        leafAt(x, y + s * 0.9f, -28f, s * 1.2f, s * 0.4f, pal.leaf); leafAt(x, y + s * 1.4f, -152f, s * 1.1f, s * 0.38f, pal.leafLight)
        flower(Bloom.White, x, y, s)
    }
    butterfly(112f, 208f, 1.3f, pal.orange); butterfly(262f, 190f, 1.1f, pal.lilac, 18f)
    mushroom(300f, 262f, 11f); stone(46f, 270f, 9f)
    blades(0f, WideW, h + 1f, 70, 6f, 20f, pal.leafDeep, pal.grassDark, 8, 4f, 2f)
    grain(WideW, h)
}
