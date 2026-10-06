package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.components.LocalSurface
import app.cove.companion.design.hueFor
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.VoiceOrb
import app.cove.companion.design.components.pressable

/** Everything the to-do list can ask for. */
class TodosActions(
    val toggle: (TodoEntity, Boolean) -> Unit,
    val open: (TodoEntity) -> Unit,
    val delete: (TodoEntity) -> Unit,
    val add: (categoryId: String, title: String) -> Unit,
    val move: (id: String, categoryId: String?, index: Int) -> Unit,
    val editCategories: () -> Unit,
    val voice: () -> Unit,
)

private const val AUTO = "auto"
private const val NONE = "none"
private val Heading = CoveType.Heading
private val CountOffset = 5.dp

/** The "To-dos" segment: one expanded category card, the others collapsed, then "Edit categories". */
@Composable
fun TodosTab(groups: List<CategoryGroup>, now: Long, drag: TodoDragState, actions: TodosActions) {
    var choice by rememberSaveable { mutableStateOf(AUTO) }
    var showAll by rememberSaveable { mutableStateOf(listOf<String>()) }
    var doneOpen by rememberSaveable { mutableStateOf(listOf<String>()) }
    var addingId by rememberSaveable { mutableStateOf<String?>(null) }
    var addText by rememberSaveable { mutableStateOf("") }
    fun <T> List<T>.toggled(v: T) = if (v in this) this - v else this + v

    val expanded = when {
        choice == NONE -> null
        choice != AUTO && groups.any { it.category.id == choice } -> choice
        else -> (groups.firstOrNull { it.open.isNotEmpty() } ?: groups.firstOrNull())?.category?.id
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (groups.isEmpty()) {
            app.cove.companion.design.components.EmptyState(
                Scene.Todos, "Nothing to do.", "Say what you need, or make a category to start.", Modifier.padding(top = 16.dp),
                primary = app.cove.companion.design.components.EmptyAction("Say it", actions.voice),
            )
        }
        groups.forEach { group ->
            val id = group.category.id
            if (id == expanded) {
                ExpandedCard(
                    group, now, drag, actions,
                    allEmpty = groups.all { it.isEmpty },
                    showAll = id in showAll,
                    doneOpen = id in doneOpen,
                    adding = addingId == id,
                    addText = addText,
                    onCollapse = { choice = NONE },
                    onShowAll = { showAll = showAll.toggled(id) },
                    onToggleDone = { doneOpen = doneOpen.toggled(id) },
                    onStartAdd = { addingId = id; addText = "" },
                    onAddText = { addText = it },
                    onCommitAdd = {
                        if (addText.isNotBlank()) actions.add(id, addText)
                        addText = ""
                    },
                    onStopAdd = { addingId = null; addText = "" },
                )
            } else {
                CollapsedCard(group, drag, onClick = { choice = id; addingId = null })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            AccentButton(
                "Edit categories", actions.editCategories,
                leading = { CoveIcon(PlanIcons.Pencil, Cove.colors.accent, size = 16.dp) },
            )
        }
    }
}

private fun countText(group: CategoryGroup) = if (group.open.isEmpty()) "all clear" else group.open.size.toString()

@Composable
private fun CollapsedCard(group: CategoryGroup, drag: TodoDragState, onClick: () -> Unit) {
    val c = Cove.colors
    val dragging = drag.id != null
    val hovered = drag.hover == group.category.id
    val shape = RoundedCornerShape(24.dp)
    val hue = c.hueFor(group.category.id)
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { drag.recordCard(group.category.id, it.boundsInRoot()) }
            .heightIn(min = 60.dp)
            .background(hue.tint, shape)
            .let { if (hovered) it.border(1.5.dp, c.ink, shape) else it }
            .pressable(onClick, onClickLabel = "Expand", role = Role.Button)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(hue.strong, CoveShapes.Circle))
        CoveText(group.category.name, Modifier.weight(1f), style = CoveType.BodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        CoveText(if (dragging) "Drop here" else countText(group), style = CoveType.Meta, color = c.muted)
    }
}

@Composable
private fun ExpandedCard(
    group: CategoryGroup,
    now: Long,
    drag: TodoDragState,
    actions: TodosActions,
    allEmpty: Boolean,
    showAll: Boolean,
    doneOpen: Boolean,
    adding: Boolean,
    addText: String,
    onCollapse: () -> Unit,
    onShowAll: () -> Unit,
    onToggleDone: () -> Unit,
    onStartAdd: () -> Unit,
    onAddText: (String) -> Unit,
    onCommitAdd: () -> Unit,
    onStopAdd: () -> Unit,
) {
    val c = Cove.colors
    val id = group.category.id
    val openIds = group.open.map { it.id }
    val visibleOpen = if (showAll) group.open else group.open.take(VISIBLE_OPEN_LIMIT)
    val empty = group.isEmpty
    val lifted = drag.id != null && drag.id in openIds
    val hue = c.hueFor(id)

    CompositionLocalProvider(LocalSurface provides hue.tint) {
    Column(
        Modifier
            .fillMaxWidth()
            .zIndex(if (lifted) 1f else 0f)
            .onGloballyPositioned { drag.recordCard(id, it.boundsInRoot()) }
            .background(hue.tint, RoundedCornerShape(28.dp))
            .padding(top = if (empty) 18.dp else 4.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (empty) it else it.heightIn(min = 56.dp).padding(top = 18.dp) }
                .pressable(onCollapse, onClickLabel = "Collapse", role = Role.Button)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.padding(top = 8.dp).size(10.dp).background(hue.strong, CoveShapes.Circle))
            CoveText(group.category.name, Modifier.weight(1f), style = Heading)
            CoveText(countText(group), Modifier.offset(y = CountOffset), style = CoveType.Meta, color = c.muted)
        }
        if (empty) EmptyState(actions.voice, big = allEmpty)
        (visibleOpen + group.doneToday).sortedBy { it.sort }.forEach { todo ->
            val index = openIds.indexOf(todo.id)
            if (index < 0) {
                DoneRow(todo, id, actions)
            } else {
                val position = if (drag.id == null || index < drag.fromIndex) index else index - 1
                TodoRowItem(
                    todo, id, openIds, position, drag, dueLabel(todo.dueAt, now),
                    onToggle = { actions.toggle(todo, it) },
                    onOpen = { actions.open(todo) },
                    onDelete = { actions.delete(todo) },
                    onDrop = { actions.move(todo.id, it.categoryId, it.index) },
                )
            }
        }
        if (group.open.size > VISIBLE_OPEN_LIMIT) {
            FooterRow(if (showAll) "Show fewer" else "Show all ${group.open.size}", showAll, onShowAll)
        }
        if (group.doneEarlier.isNotEmpty()) {
            FooterRow("${group.doneEarlier.size} done", doneOpen, onToggleDone)
            if (doneOpen) group.doneEarlier.forEach { todo -> DoneRow(todo, id, actions) }
        }
        AddRow(group.category.name, adding, addText, empty, onStartAdd, onAddText, onCommitAdd, onStopAdd)
    }
    }
}

@Composable
private fun DoneRow(todo: TodoEntity, categoryId: String, actions: TodosActions) {
    TodoRowItem(
        todo, categoryId, emptyList(), -1, InertDrag, null,
        onToggle = { actions.toggle(todo, it) },
        onOpen = { actions.open(todo) },
        onDelete = { actions.delete(todo) },
        onDrop = {},
    )
}

/** Inert drag state for rows that cannot be dragged. */
private val InertDrag = TodoDragState()

@Composable
private fun FooterRow(text: String, open: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(onClick, role = Role.Button).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoveText(text, style = CoveType.Meta, color = c.muted)
        CoveIcon(if (open) PlanIcons.ChevronUp else CoveIcons.ChevronDown, c.muted, size = 14.dp)
    }
}

@Composable
private fun AddRow(
    name: String,
    adding: Boolean,
    text: String,
    divider: Boolean,
    onStart: () -> Unit,
    onText: (String) -> Unit,
    onCommit: () -> Unit,
    onStop: () -> Unit,
) {
    val c = Cove.colors
    val style = CoveType.Body.copy(fontSize = 16.sp)
    if (divider) Hairline(Modifier.padding(horizontal = 20.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (divider) 52.dp else 48.dp)
            .let { if (adding) it else it.pressable(onStart, role = Role.Button) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoveIcon(PlanIcons.AddRow, c.tail, size = 22.dp)
        if (adding) {
            InlineField(text, onText, "Add to $name", onCommit, Modifier.weight(1f), style, onFocusLost = onStop)
        } else {
            CoveText("Add to $name", style = style, color = c.muted)
        }
    }
}

@Composable
private fun EmptyState(onVoice: () -> Unit, big: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = if (big) 20.dp else 12.dp, bottom = 28.dp).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Illustration(Scene.Todos, Modifier.height(if (big) 168.dp else 112.dp))
        CoveText(
            "Nothing here.", " Tell me what you need.",
            style = CoveType.Value.copy(lineHeight = 28.sp, letterSpacing = (-0.4).sp),
            textAlign = TextAlign.Center,
        )
        VoiceOrb(onClick = onVoice)
    }
}
