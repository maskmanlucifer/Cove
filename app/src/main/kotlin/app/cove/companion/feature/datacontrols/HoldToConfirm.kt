package app.cove.companion.feature.datacontrols

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import kotlinx.coroutines.launch

/** How long the button must be held. */
const val HOLD_MS = 1_000

/**
 * Destructive pill that fills while pressed and fires [onConfirm] once, only when full; a tap or early release does
 * nothing. Screen readers get a long-press action instead of a hold. While not [enabled] it is dimmed and inert.
 */
@Composable
fun HoldToConfirm(label: String, enabled: Boolean, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onConfirm)
    val on = rememberUpdatedState(enabled)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CoveShapes.Pill)
            .background(c.alert.copy(alpha = 0.14f))
            .semantics {
                role = Role.Button
                contentDescription = "$label. Press and hold to confirm."
                onLongClick("Confirm") { if (on.value) latest.value(); on.value }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!on.value) return@detectTapGestures
                    val run = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        latest.value()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        run.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(c.alert.copy(alpha = 0.32f)).align(Alignment.CenterStart))
        CoveText(label, style = CoveType.Button, color = c.alert, modifier = Modifier.padding(horizontal = 18.dp))
    }
}
