package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.pressable
import kotlin.math.roundToInt

private val RowHeight = 56.dp

/** Actions of the categories sheet. */
class CategoryActions(
    val add: (String) -> Unit,
    val rename: (TodoCategoryEntity, String) -> Unit,
    val reorder: (List<TodoCategoryEntity>) -> Unit,
    val delete: (TodoCategoryEntity, moveTo: String?) -> Unit,
)

/**
 * Sheet to rename, reorder (drag the grip), delete (swipe left) and add categories. Deleting one that
 * still holds to-dos asks where they should go first.
 */
@Composable
fun CategoriesSheet(groups: List<CategoryGroup>, actions: CategoryActions, onDismiss: () -> Unit) {
    var order by remember(groups.map { it.category.id }) { mutableStateOf(groups.map { it.category }) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    val counts = groups.associate { it.category.id to it.open.size }
    val totals = groups.associate { it.category.id to it.open.size + it.doneToday.size + it.doneEarlier.size }

    fun commitRename(cat: TodoCategoryEntity) {
        if (editing == cat.id) actions.rename(cat, editText)
        editing = null
    }
    fun commitAdd() {
        if (newText.isNotBlank()) actions.add(newText)
        adding = false
        newText = ""
    }

    PlanSheet(onDismiss, modifier = Modifier.fillMaxHeight().padding(top = 64.dp), gap = 16, fillHeight = true) { close ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CoveText("Categories", style = SheetTitleStyle)
            Row(Modifier.height(44.dp).pressable(close), verticalAlignment = Alignment.CenterVertically) {
                CoveText("Done", style = CoveType.Button.copy(fontSize = 16.sp))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            order.forEachIndexed { index, cat ->
                key(cat.id) {
                    if (index > 0) Hairline()
                    CategoryRow(
                        cat, index, order.size, counts[cat.id] ?: 0,
                        editing = editing == cat.id, editText = editText, onEditText = { editText = it },
                        onStartEdit = { editing = cat.id; editText = cat.name },
                        onCommitEdit = { commitRename(cat) },
                        struck = pending == cat.id,
                        onSwipeDelete = {
                            if ((totals[cat.id] ?: 0) == 0) actions.delete(cat, null) else pending = cat.id
                        },
                        onMove = { delta -> order = order.moved(index, index + delta) },
                        onDragEnd = { actions.reorder(order) },
                    )
                    if (pending == cat.id) {
                        MovePanel(
                            cat.name, totals[cat.id] ?: 0, order.filter { it.id != cat.id },
                            onMove = { pending = null; actions.delete(cat, it) },
                            onCancel = { pending = null },
                        )
                    }
                }
            }
            Hairline()
            if (adding) {
                Row(Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CoveIcon(CoveIcons.Grip, Cove.colors.placeholder, size = 18.dp)
                    FieldBox(newText, { newText = it }, "Category name", ::commitAdd, ::commitAdd)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(RowHeight).pressable({ adding = true }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CoveIcon(CoveIcons.Plus, Cove.colors.muted, size = 18.dp)
                    CoveText("New category", style = CoveType.Body, color = Cove.colors.muted)
                }
            }
        }
    }
}

/** Returns a copy with the item at [from] moved to [to] (clamped); same list if nothing moves. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    val target = to.coerceIn(0, lastIndex)
    if (target == from) return this
    return toMutableList().apply { add(target, removeAt(from)) }
}

@Composable
private fun FieldBox(value: String, onChange: (String) -> Unit, placeholder: String, onDone: () -> Unit, onFocusLost: () -> Unit) {
    InlineField(
        value, onChange, placeholder, onDone,
        Modifier.fillMaxWidth().height(44.dp).background(Cove.colors.canvas, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        onFocusLost = onFocusLost,
    )
}

@Composable
private fun CategoryRow(
    cat: TodoCategoryEntity,
    index: Int,
    size: Int,
    count: Int,
    editing: Boolean,
    editText: String,
    onEditText: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    struck: Boolean,
    onSwipeDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = Cove.colors
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    var dy by remember { mutableFloatStateOf(0f) }
    var lifted by remember { mutableStateOf(false) }
    SwipeRow(
        Modifier.zIndex(if (lifted) 1f else 0f).graphicsLayer { translationY = dy },
        onSwipeLeft = if (struck || editing) null else onSwipeDelete,
    ) {
        Row(
            Modifier.fillMaxWidth().height(RowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoveIcon(
                CoveIcons.Grip, c.placeholder,
                Modifier
                    .pointerInput(cat.id, index) {
                        detectDragGestures(
                            onDragStart = { lifted = true },
                            onDragEnd = { lifted = false; dy = 0f; onDragEnd() },
                            onDragCancel = { lifted = false; dy = 0f },
                        ) { change, amount ->
                            change.consume()
                            dy += amount.y
                            val steps = (dy / rowPx).roundToInt()
                            if (steps != 0 && index + steps in 0 until size) {
                                onMove(steps)
                                dy -= steps * rowPx
                            }
                        }
                    },
                size = 18.dp,
            )
            if (editing) {
                FieldBox(editText, onEditText, "Category name", onCommitEdit, onCommitEdit)
            } else {
                CoveText(
                    cat.name,
                    Modifier.weight(1f).pressable(onStartEdit),
                    style = CoveType.Body.copy(textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None),
                    color = if (struck) c.tail else c.ink,
                )
                CoveText(count.toString(), style = CoveType.Meta, color = c.muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovePanel(name: String, total: Int, targets: List<TodoCategoryEntity>, onMove: (String?) -> Unit, onCancel: () -> Unit) {
    val c = Cove.colors
    val noun = if (total == 1) "to-do" else "to-dos"
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).background(c.canvas, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val text = if (targets.isEmpty()) "$name has $total $noun." else "$name has $total $noun. Move ${if (total == 1) "it" else "them"} to"
        CoveText(text, style = CoveType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEachIndexed { i, t -> PanelChip(t.name, primary = i == 0) { onMove(t.id) } }
            if (targets.isEmpty()) PanelChip("Delete them", primary = true) { onMove(null) }
            Box(Modifier.height(44.dp).pressable(onCancel).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                CoveText("Cancel", style = CoveType.Meta, color = c.muted)
            }
        }
    }
}

@Composable
private fun PanelChip(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        Modifier.height(44.dp).background(if (primary) c.ink else c.card, CoveShapes.Pill).pressable(onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = if (primary) CoveType.MetaMedium else CoveType.Meta, color = if (primary) c.onInk else c.ink)
    }
}
