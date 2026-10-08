package app.cove.companion.feature.journal

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cove.companion.core.longLabel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.components.CoveText
import app.cove.companion.feature.journal.blocks.JournalBlock
import kotlinx.coroutines.CoroutineScope

/**
 * The scrolling document: date and mood, the title, then every block in order. Items are keyed by block id, so text
 * fields keep focus and caret while blocks are added, removed or dragged; only what is on screen is composed.
 *
 * @param onOpenPhoto gets the index of the tapped photo among the document's photos.
 * @param readOnly preview of a saved entry: text is selectable but not editable, blank text blocks are hidden, and
 *   media cannot be removed or dragged (photos still open, voice notes still play).
 */
@Composable
internal fun DocumentList(
    vm: JournalEditViewModel,
    s: JournalEditState,
    drag: BlockDrag,
    listState: LazyListState,
    modifier: Modifier,
    titleFocus: FocusRequester,
    onOpenPhoto: (Int) -> Unit,
    scope: CoroutineScope,
    readOnly: Boolean = false,
) {
    val doc = vm.doc
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val blocks = doc.structure
    val byId = blocks.associateBy { it.id }
    val ids = blocks.map { it.id }
    val currentIds by rememberUpdatedState(ids)
    val shown = (drag.order ?: ids).mapNotNull(byId::get)
        .filter { !readOnly || it !is JournalBlock.Text || doc.textState(it.id).text.isNotBlank() }
    val rows = s.media.associateBy { it.id }
    val photos = doc.photoRows(s.media)
    val single = blocks.size == 1

    LaunchedEffect(doc.focusRequest) {
        val id = doc.focusRequest ?: return@LaunchedEffect
        val i = shown.indexOfFirst { it.id == id }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == id }) listState.animateScrollToItem(i + HEADER_ITEMS)
    }

    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)) {
        item(key = "meta") {
            CoveText(s.day.longLabel() + (s.mood?.let { " · $it" } ?: ""), style = CoveType.Meta, color = Cove.colors.muted)
        }
        if (!readOnly || vm.title.text.isNotBlank()) item(key = "title") {
            Box(Modifier.padding(top = 16.dp)) {
                EntryField(
                    vm.title, "Title", CoveType.Title, titleFocus, Modifier.offset(y = (-2).dp), singleLine = true, readOnly = readOnly,
                    onNext = { blocks.firstOrNull { it is JournalBlock.Text }?.let { doc.focusRequest = it.id } },
                )
            }
        }
        itemsIndexed(shown, key = { _, b -> b.id }) { i, block ->
            val gap = gapAbove(shown.getOrNull(i - 1), block)
            val placement = if (drag.id == block.id || reduce) null else spring<IntOffset>(stiffness = 400f)
            val base = Modifier.animateItem(fadeInSpec = null, placementSpec = placement, fadeOutSpec = null).padding(top = gap)
            val pos = ids.indexOf(block.id)
            if (block is JournalBlock.Text) {
                val last = pos == ids.lastIndex
                TextBlock(
                    doc, block, pos,
                    placeholder = when {
                        single -> "Write whatever is on your mind."
                        pos > 0 -> "Keep writing."
                        else -> ""
                    },
                    minHeight = if (readOnly) 0.dp else if (single) 160.dp else if (last) 120.dp else 0.dp,
                    modifier = if (readOnly) base else base.moveActions(pos > 0, pos < ids.lastIndex) { doc.move(block.id, it) },
                    readOnly = readOnly,
                )
            } else {
                val photoIndex = photos.indexOfFirst { it.id == block.id }
                Box(
                    if (readOnly) base else base
                        .reorderable(drag, block.id, { currentIds }, listState, haptics, { listState.scrollBy(it) }, doc::reorder, scope)
                        .lifted(drag, block.id, reduce)
                        .moveActions(pos > 0, pos < ids.lastIndex) { doc.move(block.id, it) },
                ) {
                    MediaBlock(
                        block, rows[block.id], block.id in s.loading, s.playback, s.voiceHints[block.id],
                        onOpen = { if (photoIndex >= 0) onOpenPhoto(photoIndex) },
                        onToggle = { rows[block.id]?.let(vm::togglePlayback) },
                        onRemove = if (readOnly) null else { { vm.removeBlock(block.id) } },
                    )
                }
            }
        }
    }
}

private const val HEADER_ITEMS = 2
