package app.cove.companion.feature.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import kotlin.math.roundToInt

/**
 * One to-do line. Tap opens it, the circle ticks it, swipe right finishes it, swipe left deletes it and a
 * long press lifts it for [drag]. [position] is the row's index among the other open rows (or -1 if done)
 * and decides how far it eases aside while another row is dragged.
 */
@Composable
fun TodoRowItem(
    todo: TodoEntity,
    categoryId: String?,
    openIds: List<String>,
    position: Int,
    drag: TodoDragState,
    due: String?,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDrop: (DropTarget) -> Unit,
) {
    val c = Cove.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lifted = drag.id == todo.id
    val shiftTarget = if (position >= 0) drag.shiftFor(position) else 0f
    val animated by animateFloatAsState(shiftTarget, tween(220), label = "aside")
    val shift = if (drag.id == null) 0f else animated

    Box(
        Modifier
            .onGloballyPositioned { if (!todo.done) drag.recordRow(todo.id, it.boundsInRoot()) }
            .pointerInput(todo.id, todo.done, openIds) {
                if (todo.done) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.start(todo.id, categoryId, openIds)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.move(amount.y)
                    },
                    onDragEnd = { drag.drop()?.let(onDrop) },
                    onDragCancel = { drag.cancel() },
                )
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset(0, shift.roundToInt()) }
                .graphicsLayer {
                    if (lifted) {
                        translationY = drag.offsetY
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = with(density) { 14.dp.toPx() }
                        shape = RoundedCornerShape(18.dp)
                        clip = true
                        ambientShadowColor = Color(0x33141420)
                        spotShadowColor = Color(0x33141420)
                    }
                },
        ) {
            SwipeRow(
                onSwipeRight = { onToggle(!todo.done) },
                rightLabel = if (todo.done) "Reopen" else "Done",
                onSwipeLeft = onDelete,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(onOpen).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CheckCircle(todo.done, onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onToggle(!todo.done)
                    })
                    CoveText(todo.title, Modifier.weight(1f), color = if (todo.done) c.tail else c.ink)
                    if (lifted) {
                        CoveIcon(CoveIcons.Grip, c.tail, size = 18.dp)
                    } else if (due != null && !todo.done) {
                        CoveText(due, style = CoveType.Meta, color = c.muted)
                    }
                }
            }
        }
    }
}
