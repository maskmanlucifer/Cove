package app.cove.companion.feature.onboarding

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable

private val RowHeight = 52.dp
private val Gap = 2.dp
private val DigitStyle = CoveType.Figure.copy(fontSize = 64.sp, lineHeight = 84.sp, letterSpacing = (-2.4).sp)
private val SuffixStyle = CoveType.Body.copy(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp)
private val NeighbourStyle = CoveType.Body.copy(fontSize = 34.sp, lineHeight = 52.sp)

/**
 * Wake-time drum: the chosen time sits in a white well with two neighbours above and below.
 * Drag vertically or tap a neighbour to move in 15-minute steps; wraps around midnight.
 */
@Composable
fun WakeWheel(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val stepPx = with(LocalDensity.current) { (RowHeight + Gap).toPx() }
    var carry by remember { mutableFloatStateOf(0f) }
    val current by rememberUpdatedState(minutes)
    Column(
        modifier
            .semantics { contentDescription = "Wake-up time, ${clockText(minutes).digits}${clockText(minutes).suffix}. Swipe up or down to change." }
            .pointerInput(stepPx) {
                detectVerticalDragGestures(onDragEnd = { carry = 0f }, onDragCancel = { carry = 0f }) { _, drag ->
                    carry -= drag
                    val steps = (carry / stepPx).toInt()
                    if (steps != 0) {
                        carry -= steps * stepPx
                        onChange(stepWake(current, steps))
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Gap, Alignment.CenterVertically),
    ) {
        for (offset in -2..2) {
            val t = stepWake(minutes, offset)
            if (offset == 0) {
                Row(
                    Modifier.fillMaxWidth().height(84.dp).background(c.card, RoundedCornerShape(24.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoveText(clockText(t).digits, style = DigitStyle)
                    CoveText(clockText(t).suffix.trim(), Modifier.padding(start = 8.dp), style = SuffixStyle, color = c.tail)
                }
            } else {
                Box(Modifier.height(RowHeight).pressable({ onChange(t) }), contentAlignment = Alignment.Center) {
                    val faded = if (abs(offset) == 1) c.placeholder else c.placeholder.copy(alpha = 0.6f)
                    CoveText(clockText(t).digits, style = NeighbourStyle.copy(fontWeight = FontWeight.Normal), color = faded)
                }
            }
        }
    }
}
