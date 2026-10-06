package app.cove.companion.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.cove.companion.design.LocalReduceMotion

/** Length of the completion bloom. */
const val BloomMillis = 240

/**
 * A soft "bloom" when [done] flips to true: scale 1.0 to 1.08 to 1.0 plus a faint expanding ring in [ring].
 * Does nothing on first composition (an item that is already done) or under reduce motion.
 */
@Composable
fun Modifier.bloom(done: Boolean, ring: Color): Modifier {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(1f) }
    var last by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !last && !reduce) {
            p.snapTo(0f)
            p.animateTo(1f, tween(BloomMillis, easing = FastOutSlowInEasing))
        }
        last = done
    }
    val t = p.value
    val pulse = if (t >= 1f) 1f else 1f + 0.08f * (1f - kotlin.math.abs(2f * t - 1f))
    return this
        .drawBehind {
            if (t < 1f) {
                val r = size.minDimension / 2f
                drawCircle(ring.copy(alpha = 0.35f * (1f - t)), r * (1f + 0.7f * t), Offset(size.width / 2f, size.height / 2f), style = Stroke(1.5.dp.toPx()))
            }
        }
        .scale(pulse)
}

/**
 * Eases an item in on its first appearance only: fade plus an 8 dp rise over 180 ms, optionally [delayMillis] later.
 * The "seen" flag survives scrolling and rotation, so recycled list rows do not replay it. Skipped under reduce motion.
 */
@Composable
fun Modifier.easeIn(delayMillis: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    var seen by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (seen) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!seen) {
            p.animateTo(1f, tween(180, delayMillis, FastOutSlowInEasing))
            seen = true
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 8.dp.toPx()
    }
}
