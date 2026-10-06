package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.Cove
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.PillButton

private enum class TaskPage { Main, Category, When }

/** The floating sheet that edits one to-do: title, category, due time, reminder, Done and Delete. */
@Composable
fun TaskSheet(
    todo: TodoEntity,
    categories: List<TodoCategoryEntity>,
    now: Long,
    onSave: (TodoEntity) -> Unit,
    onSetDone: (todo: TodoEntity, done: Boolean, edited: TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onDismiss: () -> Unit,
    onCreateCategory: (String) -> Unit = {},
) {
    var title by rememberSaveable { mutableStateOf(todo.title) }
    var categoryId by rememberSaveable { mutableStateOf(todo.categoryId) }
    var dueAt by rememberSaveable { mutableStateOf(todo.dueAt) }
    var remind by rememberSaveable { mutableStateOf(todo.remind) }
    var page by rememberSaveable { mutableStateOf(TaskPage.Main) }
    var pendingNew by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categories, pendingNew) {
        val made = pendingNew?.let { n -> categories.firstOrNull { it.name == n } } ?: return@LaunchedEffect
        categoryId = made.id
        pendingNew = null
        page = TaskPage.Main
    }
    var discard by rememberSaveable { mutableStateOf(false) }

    fun edited() = todo.copy(title = title.trim().ifEmpty { todo.title }, categoryId = categoryId, dueAt = dueAt, remind = remind && dueAt != null)
    fun commit() {
        if (!discard && edited() != todo) onSave(edited())
    }

    PlanSheet(onDismiss = { commit(); onDismiss() }) { close ->
        when (page) {
            TaskPage.Category -> CategoryPage(categories, categoryId, { categoryId = it; page = TaskPage.Main }, { page = TaskPage.Main }) { name -> pendingNew = name; onCreateCategory(name) }
            TaskPage.When -> WhenPage(dueAt, now, { dueAt = it; page = TaskPage.Main }) { page = TaskPage.Main }
            TaskPage.Main -> {
                TitleField(title, { title = it }, "Title", onDone = {})
                Column {
                    SheetRow("Category", categories.firstOrNull { it.id == categoryId }?.name ?: "Inbox", { page = TaskPage.Category })
                    val whenText = whenParts(dueAt, now)
                    SheetRow("When", whenText?.first ?: "Not set", { page = TaskPage.When }, whenText?.second, placeholder = whenText == null)
                    SheetControlRow("Remind me") {
                        if (dueAt == null) CoveText("Set a time first", style = CoveType.Meta, color = Cove.colors.muted)
                        CoveSwitch(remind && dueAt != null, { remind = it }, label = "Remind me", enabled = dueAt != null)
                    }
                }
                val text = CoveType.Button.copy(fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (todo.done) "Not done" else "Done",
                        { onSetDone(todo, !todo.done, edited()); discard = true; close() },
                        Modifier.weight(1f), height = 56.dp, textStyle = text,
                    )
                    PillButton(
                        "Delete", { onDelete(todo); discard = true; close() },
                        Modifier.widthIn(min = 104.dp), kind = ButtonKind.Destructive, height = 56.dp, textStyle = text,
                    )
                }
            }
        }
    }
}
