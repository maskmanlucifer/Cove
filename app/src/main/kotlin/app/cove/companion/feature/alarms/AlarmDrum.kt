package app.cove.companion.feature.alarms

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val STEP = 15
private val SideStyle = CoveType.Section.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 40.sp, letterSpacing = 0.sp)
private val MainStyle = TextStyle(
    fontFamily = CoveType.Body.fontFamily,
    fontSize = 52.sp,
    lineHeight = 52.sp,
    letterSpacing = (-2).sp,
    lineHeightStyle = CoveType.Body.lineHeightStyle,
)

/**
 * Drum time picker: drag (or fling) the column to change the time minute by minute, or tap the
 * faded neighbours to step by 15 minutes. [minutes] is minutes since midnight.
 */
@Composable
fun DrumPicker(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val scope = rememberCoroutineScope()
    val perMinutePx = with(LocalDensity.current) { 4.dp.toPx() }
    val decay: DecayAnimationSpec<Float> = rememberSplineBasedDecay()
    var raw by remember { mutableFloatStateOf(minutes.toFloat()) }
    val fling = remember { arrayOfNulls<Job>(1) }
    fun publish() = onChange(Math.floorMod(raw.roundToInt(), 1440))
    fun step(by: Int) {
        raw = raw.roundToInt() + by.toFloat()
        publish()
    }
    val state = rememberDraggableState { delta ->
        raw -= delta / perMinutePx
        publish()
    }
    val shown = Math.floorMod(raw.roundToInt(), 1440)
    val faded = lerp(c.card, c.tail, 0.55f)

    Column(
        modifier.fillMaxWidth().draggable(
            state, Orientation.Vertical,
            onDragStarted = { fling[0]?.cancel() },
            onDragStopped = { velocity ->
                fling[0] = scope.launch {
                    AnimationState(raw, -velocity / perMinutePx).animateDecay(decay) {
                        raw = value
                        publish()
                    }
                    raw = raw.roundToInt().toFloat()
                    publish()
                }
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Side((shown - STEP + 1440) % 1440, faded) { step(-STEP) }
        Box(
            Modifier.fillMaxWidth().height(72.dp).background(c.canvas, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TimeLabel(shown, MainStyle, suffixSize = 22.sp, gap = 32.sp)
        }
        Side((shown + STEP) % 1440, faded) { step(STEP) }
    }
}

@Composable
private fun Side(minutes: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(40.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        CoveText(clockText(minutes).digits, style = SideStyle, color = color)
    }
}
