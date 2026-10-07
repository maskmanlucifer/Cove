package app.cove.companion.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the button must be held before it removes. */
const val HOLD_TO_REMOVE_MS = 1000

/**
 * Round "remove" button that needs a one second hold, so nothing is deleted by accident.
 * Pressing turns it red and fills it like a clock; letting go early cancels and shows a "Hold to remove" hint.
 * Screen-reader users remove with a plain double tap (the hold cannot be done there).
 *
 * @param label what is removed, e.g. "photo"; used for the accessibility text.
 * @param idle disc colour at rest (transparent for a bare icon).
 * @param iconColor icon colour at rest; it turns white while the disc is red.
 */
@Composable
fun HoldToRemoveButton(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = app.cove.companion.design.CoveIcons.Close,
    iconSize: Dp = 14.dp,
    disc: Dp = 32.dp,
    idle: Color = Color.Transparent,
    iconColor: Color = Cove.colors.tail,
) {
    val alert = Cove.colors.alert
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var hint by remember { mutableStateOf(false) }
    val pressed = progress.value > 0f
    val fresh = progress.value

    Box(
        modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Remove $label. Double tap to remove."
                onClick(label = "Remove $label") { onRemove(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    hint = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val run = scope.launch {
                        progress.animateTo(1f, tween(HOLD_TO_REMOVE_MS, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRemove()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        run.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                        hint = true
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(disc).clip(CoveShapes.Circle).background(if (pressed) alert.copy(alpha = 0.28f) else idle),
            contentAlignment = Alignment.Center,
        ) {
            if (pressed) {
                Canvas(Modifier.size(disc)) {
                    drawArc(alert, startAngle = -90f, sweepAngle = 360f * fresh, useCenter = true, topLeft = Offset.Zero, size = size)
                }
            }
            CoveIcon(icon, if (pressed) Color.White else iconColor, size = iconSize)
        }
        if (hint) {
            LaunchedEffect(Unit) {
                delay(1600)
                hint = false
            }
            Popup(popupPositionProvider = LeftOfAnchor(gapPx = 12)) {
                Box(Modifier.background(Cove.colors.ink, CoveShapes.Pill).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    CoveText("Hold to remove", style = CoveType.Meta, color = Cove.colors.onInk, maxLines = 1)
                }
            }
        }
    }
}

/** Places a popup just left of its anchor, centred vertically. */
private class LeftOfAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(
            (anchorBounds.left - popupContentSize.width - gapPx).coerceAtLeast(0),
            anchorBounds.center.y - popupContentSize.height / 2,
        )
}
