package app.cove.companion.design.illustrations

import kotlin.random.Random

internal fun alarmsArt() = spotScene(
    MascotPose.Sleeping, mx = 140f, ms = 1.1f, sky = { it.day(it.coralSoft, it.sunSoft) }, haze = { it.pinkSoft },
    back = { sun(120f, 118f, 26f); cloud(50f, 60f, 54f, a = 0.8f); cloud(194f, 78f, 44f, a = 0.7f); birds2() },
    ground = { alarmClock(58f, 196f, 1.15f) },
)

private fun Painter.birds2() {
    line(path { moveTo(150f, 50f); quadraticTo(154f, 45f, 158f, 50f); quadraticTo(162f, 45f, 166f, 50f) }, pal.ink, 1.1f, 0.4f)
}

internal fun voiceArt() = spotScene(
    MascotPose.Listening, mx = 142f, ms = 1.15f, sky = { it.day(it.lilacSoft, it.pinkSoft) }, haze = { it.pinkSoft },
    back = { cloud(176f, 42f, 56f); sparkle(120f, 52f, 4f, pal.sun) },
    ground = { songbird(40f, 176f) },
)

private fun Painter.songbird(x: Float, y: Float) = at(x, y) {
    fill(oval(0f, 1f, 14f, 2.6f), pal.shade)
    fill(rrect(-3f, -8f, 3f, 0f, 1.5f), pal.clay)
    fill(oval(0f, -20f, 11f, 10f), vgrad(-30f, -10f, pal.glow, pal.sun))
    fill(oval(-5f, -21f, 5f, 3.6f), pal.orange, 0.5f)
    fill(path { moveTo(10f, -22f); lineTo(17f, -19f); lineTo(10f, -17f); close() }, pal.orange)
    ds.drawCircle(pal.eye, 1.4f, androidx.compose.ui.geometry.Offset(5f, -23f))
    leafAt(-8f, -17f, 200f, 9f, 3f, pal.orange, vein = false)
    sparkle(22f, -36f, 3.6f, pal.lilac); sparkle(30f, -26f, 2.6f, pal.pink)
}

internal fun messagesArt() = spotScene(
    MascotPose.Waving, mx = 120f, ms = 1.15f, sky = { it.day(it.pinkSoft, it.warmWhite) }, haze = { it.pinkSoft },
    back = {
        cloud(60f, 54f, 48f, a = 0.8f)
        dotTrail(70f, 140f, 60f, 30f, 150f, 36f, 18, pal.coral)
        paperPlane(160f, 34f, 1f, -12f)
    },
    ground = { envelope(188f, 188f, 1.15f, 6f) },
)

internal fun syncedArt() = spotScene(
    MascotPose.Stargazing, mx = 120f, ms = 1.15f, sky = { it.day(it.leafSoft, it.warmWhite) },
    back = {
        cloud(120f, 52f, 96f)
        fill(oval(120f, 82f, 14f), vgrad(68f, 96f, pal.leafLight, pal.leaf))
        line(path { moveTo(113f, 82f); lineTo(118f, 87f); lineTo(128f, 76f) }, pal.warmWhite, 2.8f)
        dotTrail(120f, 98f, 128f, 108f, 122f, 118f, 5, pal.leaf)
        leafAt(180f, 70f, 40f, 9f, 3.4f, pal.leafLight, vein = false); leafAt(56f, 76f, 150f, 8f, 3f, pal.leaf, vein = false)
    },
)

internal fun offlineArt() = spotScene(
    MascotPose.Idle, mx = 112f, ms = 1.15f, sky = { it.day(it.mix(it.lilac, it.lilacSoft, 0.55f), it.pinkSoft) }, haze = { it.lilacSoft },
    back = {
        cloud(100f, 50f, 100f, pal.mix(pal.warmWhite, pal.lilac, 0.35f), 0.95f)
        repeat(3) { sparkle(168f + it * 8f, 30f + it * 10f, 2.8f, pal.warmWhite, 0.8f) }
        sparkle(40f, 90f, 3f, pal.warmWhite, 0.8f)
    },
    ground = {
        fill(rrect(186f, 118f, 190.5f, 196f, 2f), pal.clay)
        line(path { moveTo(188f, 124f); quadraticTo(168f, 118f, 168f, 128f) }, pal.clay, 2.4f)
        lanternProp(168f, 126f, 1.0f)
    },
)

internal fun helpArt() = spotScene(
    MascotPose.Idle, mx = 108f, ms = 1.15f, sky = { it.day(it.sunSoft, it.leafSoft) },
    back = { sun(190f, 52f, 13f); cloud(70f, 48f, 58f) },
    ground = {
        fill(rrect(190f, 150f, 194f, 200f, 2f), pal.clay)
        lifebuoy(192f, 148f, 21f)
        mushroom(40f, 196f, 9f, pal.orange)
    },
)

internal fun lanternArt() = SceneArt(S, S, ArtShape.Circle, MascotSpot(MascotPose.Lantern, 112f, 206f, 1.2f)) {
    fill(rrect(0f, 0f, S, S, 0f), vgrad(0f, 170f, pal.nightTop, pal.nightLow))
    val r = Random(77)
    repeat(26) { val x = r.nextFloat() * S; val y = r.nextFloat() * 110f; if (it % 3 == 0) sparkle(x, y, 2.4f + r.nextFloat() * 2f, pal.cream, 0.9f) else fill(oval(x, y, 0.8f), pal.cream, 0.7f) }
    moon(176f, 50f, 15f)
    rollingMeadow(S, S, 118f, pal.nightLow, 10, 1.2f, between = { glow(150f, 170f, 90f, pal.glow, 0.28f); mascot() })
    fireflies(8L, 9, 20f, 110f, 220f, 200f)
    grain(S, S)
}

internal fun secureArt() = spotScene(
    MascotPose.Idle, mx = 84f, ms = 1.15f, sky = { it.day(it.leafSoft, it.sunSoft) },
    back = { sun(60f, 50f, 12f); cloud(186f, 40f, 54f) },
    ground = { lockFlower(172f, 142f, 22f) },
)

internal fun clearedArt() = spotScene(
    MascotPose.Waving, mx = 100f, ms = 1.15f, sky = { it.day(it.warmWhite, it.leafSoft) }, flowers = 5, blooms = 1.1f,
    back = { sun(184f, 54f, 13f); cloud(80f, 46f, 56f) },
    ground = {
        sprout(170f, 198f, 28f)
        sparkle(184f, 160f, 4f, pal.sun); sparkle(158f, 168f, 2.8f, pal.pink)
    },
)

internal fun celebrationArt() = spotScene(
    MascotPose.Celebrating, mx = 120f, ms = 1.1f, sky = { it.day(it.sunSoft, it.pinkSoft) }, haze = { it.pinkSoft },
    back = {
        line(path { moveTo(10f, 34f); quadraticTo(120f, 72f, 230f, 34f) }, pal.olive, 1f, 0.6f)
        listOf(pal.coral, pal.sun, pal.lilac, pal.leaf, pal.pink, pal.orange, pal.sun).forEachIndexed { i, c ->
            val t = (i + 0.7f) / 7.4f
            val x = 10f + 220f * t
            val y = 34f + 76f * t * (1f - t)
            fill(path { moveTo(x - 7f, y); lineTo(x + 7f, y); lineTo(x, y + 14f); close() }, c)
        }
        val r = Random(5)
        repeat(18) { leafAt(r.nextFloat() * S, 70f + r.nextFloat() * 70f, r.nextFloat() * 360f, 6f, 2.4f, listOf(pal.orange, pal.pink, pal.sun, pal.lilac, pal.leaf)[it % 5], vein = false) }
    },
)

internal fun lockArt() = SceneArt(S, S, ArtShape.Circle, MascotSpot(MascotPose.Idle, 168f, 208f, 1.15f)) {
    fill(rrect(0f, 0f, S, S, 0f), vgrad(0f, 170f, pal.mix(pal.nightTop, pal.lilac, if (pal.dark) 0.1f else 0.15f), pal.mix(pal.nightLow, pal.pink, 0.35f)))
    moon(178f, 46f, 12f)
    repeat(8) { sparkle(24f + it * 27f % 200f, 20f + (it * 37f) % 80f, 2.2f, pal.cream, 0.8f) }
    rollingMeadow(S, S, 124f, pal.nightLow, 8, 1.2f, between = {
        // garden gate: two posts and a round-topped gate with a padlock
        fill(oval(86f, 200f, 52f, 6f), pal.shade)
        for (x in listOf(40f, 132f)) { fill(rrect(x - 5f, 112f, x + 5f, 202f, 3f), vgrad(112f, 202f, pal.mix(pal.clay, pal.warmWhite, 0.2f), pal.clay)); fill(oval(x, 112f, 6f, 3.4f), pal.clay) }
        val gate = path { moveTo(46f, 200f); lineTo(46f, 148f); quadraticTo(86f, 118f, 126f, 148f); lineTo(126f, 200f); close() }
        fill(gate, pal.olive, 0.35f)
        for (i in 0..5) line(path { moveTo(52f + i * 13f, 200f); lineTo(52f + i * 13f, 140f + kotlin.math.abs(i - 2.5f) * 5f) }, pal.clay, 4f)
        line(path { moveTo(46f, 168f); lineTo(126f, 168f) }, pal.clay, 3.4f)
        padlock(86f, 178f, 1.15f)
        mascot()
    })
    fireflies(9L, 7, 20f, 120f, 220f, 200f)
    grain(S, S)
}
