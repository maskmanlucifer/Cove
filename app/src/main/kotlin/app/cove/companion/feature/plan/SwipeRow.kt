package app.cove.companion.feature.plan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Row that slides sideways to reveal an action: right runs [onSwipeRight] (dark "Done" strip),
 * left runs [onSwipeLeft] (red "Delete" strip). Either can be null to disable that direction.
 * The row always settles back; the caller decides what the action does to the data.
 */
@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    onSwipeRight: (() -> Unit)? = null,
    rightLabel: String = "Done",
    onSwipeLeft: (() -> Unit)? = null,
    leftLabel: String = "Delete",
    content: @Composable () -> Unit,
) {
    val c = Cove.colors
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    val x = offset.value

    Box(modifier.fillMaxWidth()) {
        if (x != 0f) {
            Box(Modifier.matchParentSize().background(if (x > 0) c.ink else c.alert)) {
                if (x > 0) {
                    CoveText(rightLabel, Modifier.align(Alignment.CenterStart).padding(start = 20.dp), style = CoveType.MetaMedium, color = c.onInk)
                } else {
                    CoveText(leftLabel, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), style = CoveType.MetaMedium, color = if (c.isDark) c.onInk else Color.White)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .fillMaxWidth()
                .background(
                    c.card,
                    when {
                        x > 0 -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        x < 0 -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        else -> RoundedCornerShape(0.dp)
                    },
                )
                .pointerInput(onSwipeRight != null, onSwipeLeft != null) {
                    val max = if (onSwipeRight != null) threshold * 1.3f else 0f
                    val min = if (onSwipeLeft != null) -threshold * 1.3f else 0f
                    fun settle(act: Boolean) = scope.launch {
                        val v = offset.value
                        offset.animateTo(0f, tween(200))
                        if (act && v >= threshold) onSwipeRight?.invoke()
                        if (act && v <= -threshold) onSwipeLeft?.invoke()
                    }
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            val fire = (v >= threshold && onSwipeRight != null) || (v <= -threshold && onSwipeLeft != null)
                            if (fire) haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            settle(true)
                        },
                        onDragCancel = { settle(false) },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(min, max)) }
                    }
                },
        ) { content() }
    }
}
