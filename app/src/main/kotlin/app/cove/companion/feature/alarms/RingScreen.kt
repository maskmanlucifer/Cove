package app.cove.companion.feature.alarms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.BlurredEdgeTreatment
import app.cove.companion.container
import app.cove.companion.core.clockText
import app.cove.companion.core.longLabel
import app.cove.companion.core.toLocalDate
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.OrbColors
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import kotlin.math.hypot
import kotlinx.coroutines.launch

private const val HOLD_MS = 1000

private val words = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")

/**
 * Line under the time: when the first event is, in words. [hour] is the ring hour (24 h);
 * [firstEventMinutes] is the next event today, if any.
 */
fun ringMessage(hour: Int, firstEventMinutes: Int?): String {
    val phase = when {
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        else -> "Evening"
    }
    return when {
        firstEventMinutes == null -> "$phase. Nothing planned, so take it slow."
        firstEventMinutes / 60 >= 9 -> "$phase. Nothing before ${words[(firstEventMinutes / 60) % 12]}, so take it slow."
        else -> {
            val t = clockText(firstEventMinutes)
            "$phase. First up is at ${t.digits}${t.suffix}."
        }
    }
}

/** The ring screen: date, time, a calm line, hold-to-stop and snooze; [onPlayBrief] adds a quiet "Play my brief" link when the brief is on. */
@Composable
fun RingScreen(ring: RingState, message: String, onStop: () -> Unit, onSnooze: () -> Unit, onPlayBrief: (() -> Unit)? = null) {
    val c = Cove.colors
    val context = LocalContext.current
    val day = remember { context.container.clock.now().toLocalDate() }
    val time = clockText(ring.minutes)
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Glow(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(top = 72.dp, start = 28.dp, end = 28.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoveText(day.longLabel(), style = CoveType.Meta.copy(fontSize = 15.sp, lineHeight = 20.25.sp), color = c.muted)
            CoveText(
                time.digits,
                Modifier.padding(top = 8.dp),
                style = CoveType.Figure.copy(fontSize = 112.sp, lineHeight = 112.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp),
            )
            CoveText(
                message,
                Modifier.padding(top = 16.dp),
                style = CoveType.Body.copy(fontSize = 19.sp, lineHeight = 28.sp),
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f))
            onPlayBrief?.let {
                Box(Modifier.padding(bottom = 8.dp).height(44.dp).pressable(it).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    CoveText("Play my brief", style = CoveType.Body.copy(fontSize = 16.sp), color = lerp(c.muted, c.ink, 0.57f))
                }
            }
            HoldToStop(onStop)
            Box(
                Modifier.padding(top = 8.dp).height(56.dp).pressable(onSnooze).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                CoveText("Snooze ${ring.snoozeMinutes} min", style = CoveType.Body.copy(fontSize = 16.sp), color = lerp(c.muted, c.ink, 0.57f))
            }
        }
    }
}

/** Pill that fills while pressed and stops the alarm only once full; a tap or early release does nothing. */
@Composable
private fun HoldToStop(onStop: () -> Unit) {
    val c = Cove.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CoveShapes.Pill)
            .background(c.ink)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val run = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        onStop()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        run.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(lerp(c.ink, c.canvas, 0.12f))
                .align(Alignment.CenterStart),
        )
        CoveText("Hold to stop", style = CoveType.Body.copy(fontWeight = FontWeight.Medium), color = c.onInk)
    }
}

/** Soft orb glow rising from below the bottom edge, as in the design. */
@Composable
private fun Glow(modifier: Modifier) {
    Box(
        modifier
            .offset(y = 220.dp)
            .requiredSize(620.dp)
            .alpha(0.55f)
            .blur(30.dp, BlurredEdgeTreatment.Unbounded)
            .clip(CoveShapes.Circle)
            .drawBehind {
                fun blob(color: Color, cx: Float, cy: Float, stop: Float) {
                    val center = Offset(size.width * cx, size.height * cy)
                    val far = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
                    drawRect(Brush.radialGradient(0f to color, 1f to color.copy(alpha = 0f), center = center, radius = far * stop))
                }
                blob(OrbColors.Leaf, 0.50f, 0.65f, 0.50f)
                blob(OrbColors.Lilac, 0.65f, 0.45f, 0.48f)
                blob(OrbColors.Peach, 0.40f, 0.40f, 0.45f)
                blob(OrbColors.Sun, 0.58f, 0.30f, 0.42f)
            },
    )
}
