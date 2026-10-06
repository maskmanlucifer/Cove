package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.design.CoveType
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
) {
    var title by remember { mutableStateOf(todo.title) }
    var categoryId by remember { mutableStateOf(todo.categoryId) }
    var dueAt by remember { mutableStateOf(todo.dueAt) }
    var remind by remember { mutableStateOf(todo.remind) }
    var page by remember { mutableStateOf(TaskPage.Main) }
    var discard by remember { mutableStateOf(false) }

    fun edited() = todo.copy(title = title.trim().ifEmpty { todo.title }, categoryId = categoryId, dueAt = dueAt, remind = remind)
    fun commit() {
        if (!discard && edited() != todo) onSave(edited())
    }

    PlanSheet(onDismiss = { commit(); onDismiss() }) { close ->
        when (page) {
            TaskPage.Category -> CategoryPage(categories, categoryId, { categoryId = it; page = TaskPage.Main }) { page = TaskPage.Main }
            TaskPage.When -> WhenPage(dueAt, now, { dueAt = it; page = TaskPage.Main }) { page = TaskPage.Main }
            TaskPage.Main -> {
                TitleField(title, { title = it }, "Title", onDone = {})
                Column {
                    SheetRow("Category", categories.firstOrNull { it.id == categoryId }?.name ?: "None", { page = TaskPage.Category })
                    val whenText = whenParts(dueAt, now)
                    SheetRow("When", whenText?.first ?: "Not set", { page = TaskPage.When }, whenText?.second, placeholder = whenText == null)
                    SheetControlRow("Remind me") { CoveSwitch(remind, { remind = it }) }
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
                        Modifier.width(104.dp), kind = ButtonKind.Destructive, height = 56.dp, textStyle = text,
                    )
                }
            }
        }
    }
}
