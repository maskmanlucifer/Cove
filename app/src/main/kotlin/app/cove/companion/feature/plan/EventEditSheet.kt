package app.cove.companion.feature.plan

import androidx.compose.runtime.Composable
import app.cove.companion.data.local.entity.EventEntity

/**
 * Edit sheet for an existing event, opened from the Plan schedule and Today's "Move". It is the add-to-plan
 * sheet in edit mode, so the fields and pickers are the same; Save writes through [onSave], Delete through [onDelete].
 */
@Composable
fun EventEditSheet(
    event: EventEntity,
    now: Long,
    onSave: (EventEntity) -> Unit,
    onDelete: (EventEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AddToPlanSheet(
        AddDefaults(event = true, autofocus = false), emptyList(), now,
        onAddTodo = { _, _, _, _ -> },
        onAddEvent = onSave,
        onDismiss = onDismiss,
        editing = event,
        onDeleteEvent = onDelete,
    )
}
