package app.cove.companion.feature.plan

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/** Where a dragged to-do will land. */
data class DropTarget(val categoryId: String?, val index: Int)

/**
 * State of one long-press drag in the to-do list. Layout bounds are recorded as rows are placed
 * and frozen when a drag starts, so rows easing aside never feed back into the hit testing.
 */
@Stable
class TodoDragState {
    /** Id of the lifted to-do, or null when nothing is dragged. */
    var id by mutableStateOf<String?>(null)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var rowHeight by mutableFloatStateOf(0f)
        private set

    /** Category card being hovered (collapsed ones act as "Drop here" targets), or null for the open list. */
    var hover by mutableStateOf<String?>(null)
        private set

    /** Insert index among the other open rows of the dragged row's list. */
    var index by mutableIntStateOf(0)
        private set

    /** Index the dragged row had among the open rows. */
    var fromIndex by mutableIntStateOf(0)
        private set
    private var fromCategory: String? = null
    private var startCenter = 0f
    private var others: List<Pair<String, Float>> = emptyList()
    private var cards: Map<String, Rect> = emptyMap()

    private val rowBounds = HashMap<String, Rect>()
    private val cardBounds = HashMap<String, Rect>()

    fun recordRow(id: String, bounds: Rect) {
        rowBounds[id] = bounds
    }

    fun recordCard(categoryId: String, bounds: Rect) {
        cardBounds[categoryId] = bounds
    }

    /** Lifts [todoId], which is among [openIds] (in order) of [categoryId]. */
    fun start(todoId: String, categoryId: String?, openIds: List<String>) {
        val bounds = rowBounds[todoId] ?: return
        id = todoId
        fromCategory = categoryId
        offsetY = 0f
        rowHeight = bounds.height
        startCenter = bounds.center.y
        fromIndex = openIds.indexOf(todoId)
        others = openIds.filter { it != todoId }.mapNotNull { o -> rowBounds[o]?.let { o to it.center.y } }
        cards = cardBounds.filterKeys { it != categoryId }.toMap()
        hover = null
        index = fromIndex
    }

    fun move(dy: Float) {
        if (id == null) return
        offsetY += dy
        val y = startCenter + offsetY
        hover = cards.entries.firstOrNull { y >= it.value.top && y <= it.value.bottom }?.key
        index = if (hover != null) others.size else dropIndex(others.map { it.second }, y)
    }

    /** Ends the drag and returns where to put the row, or null if it was dropped where it started. */
    fun drop(): DropTarget? {
        val target = when {
            id == null -> null
            hover != null -> DropTarget(hover, Int.MAX_VALUE)
            index != fromIndex -> DropTarget(fromCategory, index)
            else -> null
        }
        cancel()
        return target
    }

    fun cancel() {
        id = null
        offsetY = 0f
        hover = null
    }

    /** Vertical shift (px) for the open row at [position] among the *other* rows, so they ease aside. */
    fun shiftFor(position: Int): Float = when {
        id == null -> 0f
        position in index until fromIndex -> rowHeight
        position in fromIndex until index -> -rowHeight
        else -> 0f
    }
}
