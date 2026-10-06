package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.DockFloatBottom
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.DebugLaunch
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes
import kotlinx.coroutines.delay

private const val ADD_EVENT = "add:event"
private const val ADD_TODO = "add:todo"
private const val CATEGORIES = "categories"
private const val TASK = "task:"
private const val EVENT = "event:"

/** Plan tab: the day's schedule and the to-do categories, with sheets to add, edit and organise. */
@Composable
fun PlanScreen(nav: Nav) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsState()
    val undo by vm.undo.collectAsState()
    var segment by rememberSaveable { mutableIntStateOf(DebugLaunch.segment ?: 0) }
    var sheet by rememberSaveable { mutableStateOf(DebugLaunch.sheet) }
    val drag = remember { TodoDragState() }
    val todos = segment == 1

    Box(Modifier.fillMaxSize()) {
        val header: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = if (todos) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText("Plan", style = CoveType.Title, modifier = Modifier.semantics { heading() })
                Segmented(listOf("Schedule", "To-dos"), segment, { segment = it }, height = 40.dp)
            }
        }
        if (todos) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .coveTopInset()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = DockClearance + 56.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                header()
                TodosTab(
                    state.groups, state.now, drag,
                    TodosActions(
                        toggle = { t, done -> vm.setDone(t, done) },
                        open = { sheet = TASK + it.id },
                        delete = { vm.delete(it) },
                        add = { cat, title -> vm.addTodo(title, cat) },
                        move = { id, cat, index -> vm.move(id, cat, index) },
                        editCategories = { sheet = CATEGORIES },
                        voice = { nav.go(Routes.Voice) },
                    ),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().imePadding().coveTopInset(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = DockClearance + 56.dp),
            ) {
                item { header(); Spacer(Modifier.height(24.dp)) }
                scheduleRows(state.timeline, { sheet = TASK + it }, { sheet = EVENT + it }, { nav.go(Routes.alarmEdit(it)) })
            }
        }
        if (!todos && drag.id == null) DayPill(state.day, state.isToday, vm::shiftDay, vm::showToday, Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = DockFloatBottom))
        if (drag.id == null) AddButton(if (todos) "Add to-do" else "Add event", { sheet = if (todos) ADD_TODO else ADD_EVENT }, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = DockFloatBottom))
        undo?.let { notice ->
            LaunchedEffect(notice.id) {
                delay(6000)
                vm.expireUndo(notice.id)
            }
            PlanUndoBar(notice.message, vm::undoLast, action = notice.action, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = DockFloatBottom))
        }
    }

    if (state.now != 0L) PlanSheets(sheet, state, vm) { sheet = null }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val shadow = if (c.isDark) Color(0x66000000) else Color(0x1A141420)
    Box(
        modifier
            .size(44.dp)
            .shadow(8.dp, CoveShapes.Circle, ambientColor = shadow, spotColor = shadow)
            .background(c.card, CoveShapes.Circle)
            .pressable(onClick, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CoveIcon(PlanIcons.AddSmall, c.muted, size = 20.dp) }
}

@Composable
private fun PlanSheets(sheet: String?, state: PlanState, vm: PlanViewModel, dismiss: () -> Unit) {
    val categories = state.groups.map { it.category }
    when {
        sheet == null -> Unit
        sheet == ADD_EVENT || sheet == ADD_TODO -> AddToPlanSheet(
            AddDefaults(sheet == ADD_EVENT, DebugLaunch.title.orEmpty(), autofocus = DebugLaunch.title == null),
            categories, state.now,
            onAddTodo = { title, cat, due, remind -> vm.addTodo(title, cat, due, remind) },
            onAddEvent = vm::addEvent,
            onDismiss = dismiss,
        )
        sheet == CATEGORIES -> CategoriesSheet(
            state.groups,
            CategoryActions(vm::addCategory, vm::renameCategory, vm::reorderCategories, vm::deleteCategory),
            dismiss,
        )
        sheet.startsWith(EVENT) -> {
            val id = sheet.removePrefix(EVENT)
            val event = remember(id, state.events.isEmpty()) { state.events.firstOrNull { it.id == id } }
            if (event != null) EventEditSheet(event, state.now, onSave = vm::saveEvent, onDelete = vm::deleteEvent, onDismiss = dismiss)
        }
        sheet.startsWith(TASK) -> {
            val key = sheet.removePrefix(TASK)
            val todo = remember(key, state.todos.isEmpty()) { state.todos.firstOrNull { it.id == key || it.title == key } }
            if (todo != null) {
                TaskSheet(
                    todo, categories, state.now,
                    onSave = vm::save,
                    onSetDone = { t, done, edited -> vm.setDone(t, done, edited) },
                    onDelete = vm::delete,
                    onDismiss = dismiss,
                )
            }
        }
    }
}
