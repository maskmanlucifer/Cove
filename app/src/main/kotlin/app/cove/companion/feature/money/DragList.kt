package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes

/**
 * Column whose rows can be dragged into a new order after a long press. The new order is reported
 * once, when the finger lifts; until the data catches up the list keeps showing the dropped order.
 *
 * @param key stable id of an item.
 * @param onReorder receives the item keys in their new order.
 * @param content draws one row; the Boolean says whether it is currently first.
 */
@Composable
fun <T> DragList(
    items: List<T>,
    key: (T) -> String,
    onReorder: (List<String>) -> Unit,
    content: @Composable (item: T, first: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val heights = remember { mutableStateMapOf<String, Int>() }
    var order by remember { mutableStateOf<List<String>?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val ids = items.map(key)

    LaunchedEffect(ids) { if (dragging == null && order == ids) order = null }

    val byId = items.associateBy(key)
    val shown = order?.mapNotNull(byId::get) ?: items

    fun move(from: Int, to: Int) {
        val list = (order ?: currentItems.map(key)).toMutableList()
        list.add(to, list.removeAt(from))
        order = list
    }

    Column {
        shown.forEachIndexed { index, item ->
            val id = key(item)
            key(id) {
                val lifted = dragging == id
                Column(
                    Modifier
                        .onSizeChanged { heights[id] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else 0f }
                        .then(if (lifted) Modifier.shadow(12.dp, CoveShapes.Card).background(Cove.colors.card) else Modifier)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = id
                                    offset = 0f
                                    order = currentItems.map(key)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val list = order ?: return@detectDragGesturesAfterLongPress
                                    val at = list.indexOf(id)
                                    val next = list.getOrNull(at + 1)
                                    val prev = list.getOrNull(at - 1)
                                    if (next != null && offset > (heights[next] ?: 0) / 2f) {
                                        offset -= heights[next] ?: 0
                                        move(at, at + 1)
                                    } else if (prev != null && offset < -(heights[prev] ?: 0) / 2f) {
                                        offset += heights[prev] ?: 0
                                        move(at, at - 1)
                                    }
                                },
                                onDragEnd = {
                                    dragging = null
                                    offset = 0f
                                    order?.let { if (it != currentItems.map(key)) currentOnReorder(it) else order = null }
                                },
                                onDragCancel = {
                                    dragging = null
                                    offset = 0f
                                    order = null
                                },
                            )
                        },
                ) { content(item, index == 0) }
            }
        }
    }
}
