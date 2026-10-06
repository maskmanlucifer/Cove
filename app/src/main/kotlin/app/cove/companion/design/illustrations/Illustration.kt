package app.cove.companion.design.illustrations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.cove.companion.design.Cove
import app.cove.companion.design.LocalReduceMotion

/** Seconds for one breathing loop of a scene's moving element. */
private const val LoopMillis = 7000

/**
 * Draws [scene] at the width the modifier gives it, keeping the scene's aspect ratio. Decorative: hidden from
 * accessibility and never interactive. Moves only very gently (one slow loop) and is still when motion is reduced,
 * when the screen is not resumed, or when [animate] is false.
 *
 * @param animate pass false for a still frame (the debug gallery and tests).
 */
@Composable
fun Illustration(scene: Scene, modifier: Modifier = Modifier, animate: Boolean = true) {
    val dark = Cove.colors.isDark
    val art = remember(scene, dark) { scene.art(ScenePalette(dark)) }
    val phase = rememberScenePhase(art.animated && animate)
    Canvas(modifier.aspectRatio(art.width / art.height).clearAndSetSemantics { }) {
        drawArt(art, phase?.value ?: 0f, Fit.Contain, 0.5f)
    }
}

/**
 * The time-of-day scene as a quiet rounded banner: fills the width the modifier gives it at [height] and crops
 * around [anchorY] (0 = top of the art, 1 = bottom) when that is shorter than the art's own ratio.
 */
@Composable
fun SceneBanner(scene: Scene, height: Dp, modifier: Modifier = Modifier, anchorY: Float = 0.7f, animate: Boolean = true) {
    val dark = Cove.colors.isDark
    val art = remember(scene, dark) { scene.art(ScenePalette(dark)) }
    val phase = rememberScenePhase(art.animated && animate)
    Canvas(
        modifier.height(height).clip(RoundedCornerShape(28.dp)).clearAndSetSemantics { },
    ) { drawArt(art, phase?.value ?: 0f, Fit.Cover, anchorY) }
}

internal enum class Fit { Contain, Cover }

private fun DrawScope.drawArt(art: Art, t: Float, fit: Fit, anchorY: Float) {
    val sx = size.width / art.width
    val sy = size.height / art.height
    val s = if (fit == Fit.Cover) maxOf(sx, sy) else minOf(sx, sy)
    val dx = (size.width - art.width * s) / 2f
    val dy = (size.height - art.height * s) * anchorY
    withTransform({
        translate(dx, dy)
        scale(s, s, Offset.Zero)
    }) { drawOps(art.ops, density / s, t) }
}

/** Looping 0..1 phase, or null (a still scene) when motion is reduced, the scene is static or the screen is not resumed. */
@Composable
private fun rememberScenePhase(wantsMotion: Boolean): State<Float>? {
    val reduce = LocalReduceMotion.current
    val resumed = rememberResumed()
    if (!wantsMotion || reduce || !resumed) return null
    return rememberInfiniteTransition(label = "scene").animateFloat(
        0f, 1f, infiniteRepeatable(tween(LoopMillis, easing = LinearEasing), RepeatMode.Restart), label = "phase",
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
