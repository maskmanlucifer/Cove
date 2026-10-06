package app.cove.companion.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis

/**
 * A question bar: a dark pill that slides down from the top under the status bar, with a message and an action.
 * Used for decisions that need an answer (for example "Tag earlier payments too?"), not for Undo. Place it with `Modifier.align(Alignment.TopCenter)` in a full-screen `Box`; [belowHeader] pushes it under a screen's
 * own title/back row. An optional [secondaryAction] (for example "Not now") sits before the main action. Callers own the timing (auto-dismiss) and compose it only while an offer exists, or pass [visible].
 */
@Composable
fun PromptBar(
    message: String,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: String = "Undo",
    belowHeader: Boolean = false,
    visible: Boolean = true,
    secondaryAction: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = Cove.colors
    val reduce = LocalReduceMotion.current
    val inset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val ms = if (reduce) ReducedMotionMillis else 220
    Box(modifier.fillMaxWidth().padding(top = UndoPlacement.top(inset, belowHeader), start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            state,
            enter = fadeIn(tween(ms)) + if (reduce) androidx.compose.animation.EnterTransition.None else slideInVertically(tween(ms)) { -it / 2 },
            exit = fadeOut(tween(ms)) + if (reduce) androidx.compose.animation.ExitTransition.None else slideOutVertically(tween(ms)) { -it / 2 },
        ) {
            val shadow = Color(0x33141420)
            Row(
                Modifier
                    .widthIn(max = UndoPlacement.MaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .shadow(24.dp, CoveShapes.Pill, ambientColor = shadow, spotColor = shadow)
                    .background(c.ink, CoveShapes.Pill)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoveText(
                    message, Modifier.weight(1f).padding(vertical = 8.dp),
                    style = CoveType.Button.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp), color = c.onInk, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (secondaryAction != null && onSecondary != null) {
                    Box(
                        Modifier.heightIn(min = 40.dp).pressable(onSecondary, role = Role.Button).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { CoveText(secondaryAction, style = CoveType.Button.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal), color = c.onInk.copy(alpha = 0.7f)) }
                }
                if (onAction != null) {
                    Box(
                        Modifier.heightIn(min = 40.dp).background(c.onInk.copy(alpha = 0.14f), CoveShapes.Pill).pressable(onAction, role = Role.Button).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CoveText(action, style = CoveType.Button.copy(fontSize = 15.sp), color = c.onInk) }
                }
            }
        }
    }
}
