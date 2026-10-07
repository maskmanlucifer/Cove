package app.cove.companion.feature.journal

import app.cove.companion.design.components.HoldToRemoveButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.PlaybackState
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.cove.companion.feature.journal.blocks.JournalBlock

/** Gap above a block: 12 dp between two media blocks, 16 dp otherwise. */
internal fun gapAbove(previous: JournalBlock?, block: JournalBlock): Dp =
    if (previous != null && previous !is JournalBlock.Text && block !is JournalBlock.Text) 12.dp else 16.dp

/** Drag-to-reorder state of the document: the lifted block, the order shown meanwhile, and its finger offset. */
@Stable
class BlockDrag {
    var id by mutableStateOf<String?>(null)
    var order by mutableStateOf<List<String>?>(null)
    var offset by mutableFloatStateOf(0f)
}

/**
 * Long-press a media block, then drag it between the other blocks. Others ease aside (lazy item placement), the order
 * is applied once, when the finger lifts ([onDrop]); [scroll] moves the list near its edges and returns what it moved.
 */
internal fun Modifier.reorderable(
    drag: BlockDrag,
    id: String,
    ids: () -> List<String>,
    state: LazyListState,
    haptics: HapticFeedback,
    scroll: suspend (Float) -> Float,
    onDrop: (List<String>) -> Unit,
    scope: CoroutineScope,
): Modifier = pointerInput(id) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.id = id
            drag.offset = 0f
            drag.order = ids()
        },
        onDrag = { change, amount ->
            change.consume()
            drag.offset += amount.y
            val list = drag.order ?: return@detectDragGesturesAfterLongPress
            val at = list.indexOf(id)
            fun size(key: String?) = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.size ?: 0
            val next = list.getOrNull(at + 1)
            val prev = list.getOrNull(at - 1)
            if (next != null && drag.offset > size(next) / 2f) {
                drag.offset -= size(next)
                drag.order = list.toMutableList().also { it.add(at + 1, it.removeAt(at)) }
            } else if (prev != null && drag.offset < -size(prev) / 2f) {
                drag.offset += size(prev)
                drag.order = list.toMutableList().also { it.add(at - 1, it.removeAt(at)) }
            }
            val me = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            if (me != null) {
                val top = me.offset + drag.offset
                val bottom = top + me.size
                val edge = 72.dp.toPx()
                val view = state.layoutInfo.viewportEndOffset
                val d = when {
                    top < edge -> -16f
                    bottom > view - edge -> 16f
                    else -> 0f
                }
                if (d != 0f) scope.launch { drag.offset += scroll(d) }
            }
        },
        onDragEnd = {
            val order = drag.order
            drag.id = null
            drag.offset = 0f
            if (order != null && order != ids()) onDrop(order)
            drag.order = null
        },
        onDragCancel = {
            drag.id = null
            drag.offset = 0f
            drag.order = null
        },
    )
}

/** The lift look while dragging: scale 1.02, soft shadow, above the rest. */
@Composable
internal fun Modifier.lifted(drag: BlockDrag, id: String, reduceMotion: Boolean): Modifier {
    if (drag.id != id) return this
    val shadow = Cove.colors.shadow
    return this
        .zIndex(1f)
        .graphicsLayer {
            translationY = drag.offset
            if (!reduceMotion) { scaleX = 1.02f; scaleY = 1.02f }
            shadowElevation = 14.dp.toPx()
            shape = RoundedCornerShape(24.dp)
            ambientShadowColor = shadow
            spotShadowColor = shadow
        }
}

/** Accessibility actions that move a block up or down; only the possible ones are offered. */
internal fun Modifier.moveActions(canUp: Boolean, canDown: Boolean, move: (Int) -> Unit): Modifier = semantics {
    customActions = buildList {
        if (canUp) add(CustomAccessibilityAction("Move up") { move(-1); true })
        if (canDown) add(CustomAccessibilityAction("Move down") { move(1); true })
    }
}

/**
 * Grows with its content. Backspace at the very start of a text block that follows media does not delete the media:
 * the caret goes to the end of the text before it. Reports focus so the chips know where to insert.
 */
@Composable
internal fun TextBlock(
    doc: JournalDocument,
    block: JournalBlock.Text,
    index: Int,
    placeholder: String,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val state = doc.textState(block.id)
    LaunchedEffect(doc.focusRequest) {
        if (doc.focusRequest == block.id) {
            focus.requestFocus()
            doc.focusRequest = null
        }
    }
    EntryField(
        state, placeholder, BodyStyle, focus,
        modifier
            .offset(y = (-3).dp).heightIn(min = minHeight)
            .onFocusChanged { if (it.isFocused) doc.anchorId = block.id }
            .onPreviewKeyEvent { e ->
                val atStart = state.selection.collapsed && state.selection.start == 0
                if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && atStart && index > 0) {
                    doc.previousTextId(block.id)?.let { prev ->
                        doc.textState(prev).edit { selection = TextRange(length) }
                        doc.focusRequest = prev
                    }
                    true
                } else {
                    false
                }
            },
        bodyColor(), shortBlankLines = true,
    )
}

/** A photo or voice note whose row is not here: still being stored ([loading]) or not available (not synced yet). */
@Composable
internal fun MediaPlaceholder(label: String, onRemove: (() -> Unit)?) {
    val c = Cove.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).background(c.well, RoundedCornerShape(24.dp)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CoveText(label, style = CoveType.Meta, color = c.muted)
        if (onRemove != null) {
            HoldToRemoveButton("item", onRemove, Modifier.align(Alignment.TopEnd).padding(2.dp))
        }
    }
}

/** One media block of the document: photo, voice row or placeholder. */
@Composable
internal fun MediaBlock(
    block: JournalBlock,
    row: JournalMediaEntity?,
    loading: Boolean,
    playback: PlaybackState,
    hint: String?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val voice = row?.kind == "voice" || (row == null && block is JournalBlock.Voice)
    when {
        row == null -> MediaPlaceholder(
            when {
                loading && voice -> "Adding voice note…"
                loading -> "Adding photo…"
                else -> "Not available yet"
            },
            onRemove.takeUnless { loading },
        )
        voice -> VoiceRow(row, playback, onToggle, onRemove, hint)
        else -> JournalPhoto(row, onOpen, onRemove)
    }
}
