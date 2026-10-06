package app.cove.companion.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import app.cove.companion.R
import app.cove.companion.container

/** "Today · 3 left" with checkable to-do rows; ticking runs [ToggleTodoAction] without opening the app. */
class TasksWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load(context.container)
        provideContent {
            val rows = ((LocalSize.current.height.value - 56) / ROW_DP).toInt().coerceIn(1, 6)
            TasksContent(snapshot.tasks.take(rows), snapshot.tasksLeft)
        }
    }

    private companion object {
        const val ROW_DP = 40
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

private val TodoIdKey = ActionParameters.Key<String>("todo_id")
private val DoneKey = ActionParameters.Key<Boolean>("todo_done")

/** Flips a to-do from the tasks widget through the repository, then redraws the widgets. */
class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TodoIdKey] ?: return
        app.cove.companion.resilience.CrashHandler.guarded("widget-toggle") {
            context.container.todos.setDone(id, !(parameters[DoneKey] ?: false))
            WidgetUpdater.refresh(context)
        }
    }
}

@Composable
private fun TasksContent(rows: List<TaskRow>, left: Int) {
    Column(modifier = cardModifier(18).fillMaxSize()) {
        Text(
            if (left == 0) "Today · all done" else "Today · $left left",
            style = WidgetText.label,
            modifier = GlanceModifier.fillMaxWidth().clickable(openApp()),
        )
        Spacer(GlanceModifier.height(4.dp))
        if (rows.isEmpty()) Text("Nothing on the list", style = TextStyle(fontSize = 17.sp, color = WidgetColors.muted))
        rows.forEach { TaskLine(it) }
    }
}

@Composable
private fun TaskLine(row: TaskRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(40.dp)
            .clickable(actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdKey to row.id, DoneKey to row.done))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.size(22.dp).cornerRadius(11.dp).background(if (row.done) WidgetColors.ink else WidgetColors.tail), contentAlignment = Alignment.Center) {
            if (row.done) {
                Image(ImageProvider(R.drawable.widget_check), contentDescription = "Done", colorFilter = ColorFilter.tint(WidgetColors.onInk), modifier = GlanceModifier.size(14.dp))
            } else {
                Box(modifier = GlanceModifier.size(19.dp).cornerRadius(9.5.dp).background(WidgetColors.card)) {}
            }
        }
        Spacer(GlanceModifier.width(12.dp))
        Text(
            row.title, maxLines = 1,
            style = TextStyle(
                fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = if (row.done) WidgetColors.tail else WidgetColors.ink,
                textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
    }
}
