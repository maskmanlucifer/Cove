package app.cove.companion.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.OrbColors
import kotlin.math.hypot

/** Draws the design's three-blob gradient inside the current draw bounds. */
fun DrawScope.drawOrbGradient() {
    drawRect(OrbColors.Base)
    fun blob(color: Color, cx: Float, cy: Float, stop: Float) {
        val center = Offset(size.width * cx, size.height * cy)
        val farthest = hypot(
            maxOf(center.x, size.width - center.x),
            maxOf(center.y, size.height - center.y),
        )
        drawRect(
            Brush.radialGradient(
                0f to color, 1f to color.copy(alpha = 0f),
                center = center, radius = farthest * stop,
            ),
        )
    }
    blob(OrbColors.Sky, 0.50f, 0.75f, 0.60f)
    blob(OrbColors.Lilac, 0.70f, 0.45f, 0.60f)
    blob(OrbColors.Peach, 0.35f, 0.35f, 0.55f)
}

/** Voice orb button. [breathing] animates a slow 4 s pulse while idle-listening. */
@Composable
fun VoiceOrb(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    breathing: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scale = if (breathing) {
        rememberInfiniteTransition(label = "orb").animateFloat(
            1f, 1.06f,
            infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "scale",
        ).value
    } else 1f
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CoveShapes.Circle)
            .let { if (onClick != null) it.pressable(onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) { drawOrbGradient() }
        CoveIcon(CoveIcons.Mic, androidx.compose.ui.graphics.Color(0xFF16171A), size = if (size < 48.dp) 18.dp else 24.dp)
    }
}
