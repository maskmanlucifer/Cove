package app.cove.companion.design.illustrations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.cove.companion.design.Cove
import app.cove.companion.design.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws [scene] at the width the modifier gives it, keeping the scene's aspect ratio. Decorative: hidden from
 * accessibility and never interactive. The art is a cached bitmap; only the mascot's blink and antenna sway move,
 * and they stop under reduce-motion, when the screen is not resumed, or when [animate] is false.
 *
 * @param animate pass false for a still frame (the debug gallery and tests).
 */
@Composable
fun Illustration(scene: Scene, modifier: Modifier = Modifier, animate: Boolean = true) {
    val art = scene.art
    ArtView(scene.name, art, modifier.aspectRatio(art.w / art.h), Fit.Contain, 0.5f, true, animate)
}

/**
 * The time-of-day scene as a quiet rounded banner: fills the width the modifier gives it at [height] and crops
 * around [anchorY] (0 = top of the art, 1 = bottom) when that is shorter than the art's own ratio. Thin strips
 * (under 100 dp) show the meadow only, without the mascot.
 */
@Composable
fun SceneBanner(
    scene: Scene, height: Dp, modifier: Modifier = Modifier, anchorY: Float = 0.7f, animate: Boolean = true,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    val strip = height < 100.dp
    ArtView(
        scene.name, scene.art, modifier.height(height).clip(shape),
        Fit.Cover, if (strip) 1f else anchorY, !strip, animate,
    )
}

/** The companion alone, in [pose], keeping its aspect ratio at the size the modifier gives it. */
@Composable
fun Mascot(pose: MascotPose, modifier: Modifier = Modifier, animate: Boolean = true) {
    val art = remember(pose) { mascotArt(pose) }
    ArtView("mascot-${pose.name}", art, modifier.aspectRatio(art.w / art.h), Fit.Contain, 0.5f, true, animate)
}

/** Just the companion's head and antenna: a small warm spot beside a greeting. */
@Composable
fun MascotHead(modifier: Modifier = Modifier, pose: MascotPose = MascotPose.Idle, animate: Boolean = true) {
    val art = remember(pose) { mascotHeadArt(pose) }
    ArtView("head-${pose.name}", art, modifier.aspectRatio(art.w / art.h), Fit.Contain, 0.5f, true, animate)
}

internal fun mascotArt(pose: MascotPose) = SceneArt(150f, 134f, ArtShape.Free, MascotSpot(pose, 75f, 128f)) { mascot() }

internal fun mascotHeadArt(pose: MascotPose) = SceneArt(84f, 84f, ArtShape.Circle, MascotSpot(pose, 42f, 126f)) {
    fill(oval(42f, 42f, 42f), vgrad(0f, 84f, pal.leafSoft, pal.mix(pal.leafSoft, pal.leafLight, 0.5f)))
    mascot()
}

@Composable
internal fun ArtView(id: String, art: SceneArt, modifier: Modifier, fit: Fit, anchorY: Float, withMascot: Boolean, animate: Boolean) {
    val dark = Cove.colors.isDark
    var box by remember { mutableStateOf(IntSize.Zero) }
    val key = ArtKey(id, box.width, box.height, dark, if (withMascot) 1 else 0)
    val bitmap by produceState(if (box.width > 0) ArtCache.get(key) else null, key) {
        if (box.width > 0) {
            value = ArtCache.get(key) ?: withContext(Dispatchers.Default) {
                renderArt(art, dark, box.width, box.height, fit, anchorY, withMascot)
            }.also { ArtCache.put(key, it) }
        }
    }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(200), label = "art")
    val phase = rememberIdlePhase(animate && withMascot && art.mascot != null)
    Box(modifier.onSizeChanged { box = it }.clearAndSetSemantics { }) {
        Canvas(Modifier.matchParentSize()) { bitmap?.let { drawImage(it, alpha = alpha) } }
        val spot = art.mascot
        if (spot != null && withMascot) Canvas(Modifier.matchParentSize()) {
            val t = phase?.value ?: 0f
            val f = fitTransform(art.w, art.h, size.width, size.height, fit, anchorY)
            withTransform({
                translate(f.dx + spot.x * f.scale, f.dy + spot.y * f.scale)
                scale(f.scale * spot.scale, f.scale * spot.scale, Offset.Zero)
            }) { drawMascotLive(IllusPalette(dark), spot.pose, spot.flip, blinkAt(t), swayAt(t)) }
        }
    }
}

/** Paints [art] once into a bitmap of the given pixel size (call off the main thread). */
internal fun renderArt(art: SceneArt, dark: Boolean, wPx: Int, hPx: Int, fit: Fit, anchorY: Float, withMascot: Boolean): ImageBitmap {
    val bmp = ImageBitmap(wPx, hPx)
    val f = fitTransform(art.w, art.h, wPx.toFloat(), hPx.toFloat(), fit, anchorY)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GfxCanvas(bmp), Size(wPx.toFloat(), hPx.toFloat())) {
        withTransform({
            translate(f.dx, f.dy)
            scale(f.scale, f.scale, Offset.Zero)
        }) {
            val p = Painter(this, IllusPalette(dark))
            p.spot = art.mascot
            p.showMascot = withMascot
            p.apply(art.paint)
            if (art.shape == ArtShape.Circle) {
                val hole = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(-20f, -20f, art.w + 20f, art.h + 20f))
                    addOval(Rect(0f, 0f, art.w, art.h))
                }
                drawPath(hole, Color.Black, blendMode = BlendMode.Clear)
            }
        }
    }
    return bmp
}

/** Looping 0..1 idle phase, or null (a still mascot) when motion is reduced, nothing moves, or the screen is not resumed. */
@Composable
private fun rememberIdlePhase(wantsMotion: Boolean): State<Float>? {
    val reduce = LocalReduceMotion.current
    val resumed = rememberResumed()
    if (!wantsMotion || reduce || !resumed) return null
    return rememberInfiniteTransition(label = "idle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(IdleLoopMillis, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
}

@Composable
private fun rememberResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed
}
