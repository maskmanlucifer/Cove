package app.cove.companion.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where Undo bars sat: top of the screen, just under the status bar. Pure so it can be unit tested. */
object UndoPlacement {
    /** Widest the bar gets, so it stays a centred pill on large screens. */
    val MaxWidth = 360.dp

    /** Space between the status bar and the bar. */
    val Gap = 8.dp

    /** Extra offset for screens with a title or back/close row, so those buttons stay tappable. */
    val HeaderClearance = 56.dp

    /** Distance from the screen top to the bar's top edge. */
    fun top(statusInset: Dp, belowHeader: Boolean): Dp = maxOf(statusInset, 24.dp) + Gap + if (belowHeader) HeaderClearance else 0.dp
}

/**
 * Retired at the owner's request: Undo bars ("Added ..., Undo") were irritating, so nothing is drawn any more. Every
 * screen still funnels its notices through this one place, so a bar can be brought back by restoring this body from the
 * git history of this file. Deletes that need care use a confirmation sheet instead.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun TopUndoBar(
    message: String,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: String = "Undo",
    belowHeader: Boolean = false,
    visible: Boolean = true,
) = Unit
