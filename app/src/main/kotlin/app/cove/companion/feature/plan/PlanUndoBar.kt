package app.cove.companion.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cove.companion.design.components.TopUndoBar

/** Undo bar with a message and one action; a thin wrapper over the shared [TopUndoBar]. */
@Composable
fun PlanUndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier, action: String = "Undo", belowHeader: Boolean = true) =
    TopUndoBar(message, onUndo, modifier, action, belowHeader)
