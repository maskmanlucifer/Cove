package app.cove.companion.design.illustrations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal const val S = 240f

/**
 * A round "avatar" vignette: sky gradient from [sky], [back] (sun, clouds), rolling meadow, then [ground] props
 * standing on the grass, the mascot, and [front] props, with a paper grain over it all.
 */
internal fun spotScene(
    pose: MascotPose, mx: Float = 122f, my: Float = 206f, ms: Float = 1.2f, flip: Boolean = false,
    sky: (IllusPalette) -> Pair<Color, Color>, haze: (IllusPalette) -> Color = { it.leafSoft },
    horizon: Float = 112f, flowers: Int = 12, blooms: Float = 1.25f,
    back: Painter.() -> Unit = {}, ground: Painter.() -> Unit = {}, front: Painter.() -> Unit = {},
) = SceneArt(S, S, ArtShape.Circle, MascotSpot(pose, mx, my, ms, flip)) {
    val (top, low) = sky(pal)
    fill(rrect(0f, 0f, S, S, 0f), vgrad(0f, 160f, top, low))
    back()
    rollingMeadow(S, S, horizon, haze(pal), flowers, blooms, between = { ground(); mascot(); front() })
    grain(S, S)
}

internal fun IllusPalette.day(a: Color, b: Color): Pair<Color, Color> =
    if (dark) mix(nightTop, a, 0.32f) to mix(nightTop, b, 0.4f) else a to b

internal fun todosArt() = spotScene(
    MascotPose.Tending, mx = 112f, ms = 1.12f, sky = { it.day(it.leafSoft, it.sunSoft) },
    back = { sun(188f, 56f, 14f); cloud(66f, 52f, 60f) },
    ground = { notebook(52f, 176f) },
)

internal fun scheduleArt() = spotScene(
    MascotPose.Thinking, mx = 136f, ms = 1.15f, sky = { it.day(it.lilacSoft, it.pinkSoft) }, haze = { it.pinkSoft },
    back = { cloud(176f, 40f, 62f); cloud(46f, 78f, 40f, a0 = 0.7f); sparkle(94f, 40f, 4f, pal.sun) },
    ground = { calendarPost(58f, 190f) },
)

internal fun moneyArt() = spotScene(
    MascotPose.Coin, mx = 138f, ms = 1.15f, sky = { it.day(it.sunSoft, it.warmWhite) }, haze = { it.sunSoft },
    back = { sun(60f, 52f, 13f); cloud(184f, 44f, 56f) },
    ground = { jar(62f, 196f) },
)

internal fun journalArt() = SceneArt(S, S, ArtShape.Circle, MascotSpot(MascotPose.Reading, 128f, 212f, 1.2f)) {
    fill(rrect(0f, 0f, S, S, 0f), vgrad(0f, S, pal.mix(pal.pinkSoft, pal.coralSoft, 0.4f), pal.mix(pal.coralSoft, pal.sunSoft, 0.6f)))
    strokes(strokeField(61L, 90, 0f, 0f, S, 170f, 6f..14f, -90f, 8f, 1f..2.2f, 0.05f..0.13f), pal.clay, pal.sheet)
    // arched window
    val win = path { moveTo(66f, 160f); lineTo(66f, 66f); quadraticTo(66f, 14f, 120f, 14f); quadraticTo(174f, 14f, 174f, 66f); lineTo(174f, 160f); close() }
    fill(win, pal.sheet)
    val glass = path { moveTo(74f, 154f); lineTo(74f, 68f); quadraticTo(74f, 22f, 120f, 22f); quadraticTo(166f, 22f, 166f, 68f); lineTo(166f, 154f); close() }
    fill(glass, vgrad(22f, 154f, pal.day(pal.sky, pal.sunSoft).first, pal.day(pal.sky, pal.sunSoft).second))
    clip(glass) {
        sun(140f, 62f, 11f, rays = false)
        cloud(100f, 52f, 40f)
        hill(pts(70f, 118f, 110f, 106f, 170f, 112f), 160f, greens(0.2f).first, greens(0.2f).second, pal.leaf, pal.sheet, 30)
        hill(pts(70f, 134f, 120f, 124f, 170f, 132f), 160f, greens(0.6f).first, greens(0.6f).second, pal.leafDeep, pal.leaf, 40)
        scatter(62L, 9, 72f, 168f, 128f, 150f, 2.4f, 3.4f, listOf(Bloom.Daisy, Bloom.Lilac, Bloom.Yellow, Bloom.Pink))
    }
    line(glass, pal.olive, 1.4f, 0.28f)
    line(path { moveTo(120f, 22f); lineTo(120f, 154f); moveTo(74f, 92f); lineTo(166f, 92f) }, pal.sheet, 3f)
    fill(rrect(58f, 158f, 182f, 168f, 4f), pal.cream)
    fill(rrect(58f, 166f, 182f, 169f, 1.5f), pal.shade)
    // pot of flowers on the sill
    fill(path { moveTo(82f, 158f); lineTo(104f, 158f); lineTo(101f, 144f); lineTo(85f, 144f); close() }, pal.coral)
    for ((x, y, k) in listOf(Triple(86f, 118f, Bloom.Lilac), Triple(94f, 108f, Bloom.White), Triple(101f, 123f, Bloom.Pink))) { stemmed(k, x, y, 8f, 146f) }
    // floor and rug
    fill(rrect(0f, 176f, S, S, 0f), vgrad(176f, S, pal.mix(pal.clay, pal.sheet, 0.35f), pal.clay))
    line(path { moveTo(0f, 176f); lineTo(S, 176f) }, pal.sheet, 1.5f, 0.5f)
    fill(oval(124f, 214f, 78f, 16f), pal.mix(pal.leaf, pal.leafSoft, 0.4f))
    line(path { addOval(androidx.compose.ui.geometry.Rect(54f, 202f, 194f, 226f)) }, pal.sheet, 1.4f, 0.6f)
    mascot()
    // a few fallen petals and a sprig in a cup beside the mascot
    leafAt(60f, 214f, 12f, 9f, 3f, pal.pink, vein = false); leafAt(190f, 218f, 190f, 8f, 3f, pal.sun, vein = false)
    grain(S, S)
}

internal fun habitsArt() = spotScene(
    MascotPose.Walking, mx = 82f, my = 202f, ms = 1.1f, sky = { it.day(it.sunSoft, it.leafSoft) }, flowers = 18,
    back = { sun(184f, 50f, 13f); cloud(70f, 44f, 54f) },
    ground = {
        for ((x, y, r) in listOf(Triple(150f, 226f, 24f), Triple(166f, 206f, 20f), Triple(150f, 188f, 16f), Triple(160f, 174f, 12f), Triple(154f, 164f, 8f))) {
            stone(x, y, r, r * 0.46f, pal.mix(pal.sage, pal.cream, 0.4f))
        }
        mushroom(190f, 190f, 10f)
    },
)

internal fun trainingArt() = spotScene(
    MascotPose.Lifting, mx = 118f, ms = 1.15f, sky = { it.day(it.sunSoft, it.leafSoft) },
    back = { sun(50f, 56f, 15f); cloud(180f, 48f, 62f) },
    ground = {
        fill(oval(122f, 205f, 70f, 11f), pal.shade)
        fill(oval(120f, 202f, 68f, 10f), vgrad(192f, 212f, pal.coral, pal.mix(pal.coral, pal.clay, 0.5f)))
        line(path { addOval(androidx.compose.ui.geometry.Rect(60f, 193f, 180f, 211f)) }, pal.sheet, 1.4f, 0.55f)
        // water bottle
        fill(rrect(196f, 176f, 212f, 206f, 5f), vgrad(176f, 206f, pal.sky, pal.mix(pal.sky, pal.lilac, 0.4f)))
        fill(rrect(199f, 170f, 209f, 178f, 2f), pal.leafDeep)
    },
)
