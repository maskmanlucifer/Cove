package app.cove.companion.feature.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.OrbColors
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import kotlin.math.hypot

private val Transcript = CoveType.Title.copy(fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp)

/** Frame 02: gradient wash, live transcript with the newest word grey, timer and controls. */
@Composable
fun ListeningView(s: VoiceState, onClose: () -> Unit, onType: () -> Unit, onFinish: () -> Unit) {
    val c = Cove.colors
    Box(Modifier.fillMaxSize()) {
        VoiceWash(s.level, Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CoveText(if (!s.ready) "Getting ready" else if (s.onDevice) "Listening · on this phone" else "Listening", style = CoveType.Meta, color = c.muted)
                CloseButton(onClose)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
                LiveTranscript(s.transcript, s.ready)
                CoveText("%d:%02d".format(s.seconds / 60, s.seconds % 60), style = CoveType.Body.copy(fontSize = 15.sp), color = c.muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                GlassPill("Type instead", onType)
                Box(Modifier.size(76.dp).background(c.ink, CoveShapes.Circle).pressable(onFinish, role = Role.Button).semantics { contentDescription = "Stop listening" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(22.dp).background(c.onInk, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                }
                GlassPill("Done", onFinish)
            }
        }
    }
}

@Composable
private fun LiveTranscript(text: String, ready: Boolean) {
    val c = Cove.colors
    if (text.isBlank()) {
        CoveText(if (ready) "Say it now" else "Getting ready…", style = Transcript, color = c.tail)
        return
    }
    val cut = text.trimEnd().lastIndexOf(' ') + 1
    CoveText(text.substring(0, cut), text.substring(cut).trimEnd() + "…", style = Transcript, secondaryColor = c.tail)
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).background(Cove.colors.card.copy(alpha = 0.7f), CoveShapes.Pill).pressable(onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { CoveText(text, style = CoveType.Body.copy(fontSize = 15.sp, lineHeight = 20.25.sp)) }
}

/** The orb gradient scaled up and blurred at the bottom edge; it breathes slowly and swells with [level]. */
@Composable
private fun VoiceWash(level: Float, modifier: Modifier) {
    val breathe by rememberInfiniteTransition(label = "wash").animateFloat(
        1f, 1.05f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "breathe",
    )
    val swell by animateFloatAsState(1f + 0.18f * level, tween(120), label = "swell")
    val alpha = if (Cove.colors.isDark) 0.4f else 0.9f
    Canvas(
        modifier
            .offset(y = 180.dp)
            .size(560.dp)
            .graphicsLayer { scaleX = breathe * swell; scaleY = breathe * swell; this.alpha = alpha }
            .blur(20.dp)
            .clip(CoveShapes.Circle),
    ) { drawWash() }
}

private fun DrawScope.drawWash() {
    fun blob(color: Color, cx: Float, cy: Float, stop: Float) {
        val center = Offset(size.width * cx, size.height * cy)
        val farthest = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
        drawRect(Brush.radialGradient(0f to color, 1f to color.copy(alpha = 0f), center = center, radius = farthest * stop))
    }
    blob(OrbColors.Leaf, 0.50f, 0.65f, 0.50f)
    blob(OrbColors.Lilac, 0.65f, 0.45f, 0.48f)
    blob(OrbColors.Peach, 0.40f, 0.40f, 0.45f)
    blob(OrbColors.Sun, 0.58f, 0.30f, 0.42f)
}
