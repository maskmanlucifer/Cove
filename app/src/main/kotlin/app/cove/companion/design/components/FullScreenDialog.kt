package app.cove.companion.design.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.abs

/**
 * Black, edge-to-edge dialog for the photo viewer and the crop step. Back dismisses it; light status-bar icons are
 * used on the black scrim, and the window copies the activity's `FLAG_SECURE` so the app lock still hides it.
 */
@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val activity = LocalContext.current.let { it as? Activity }
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(0f)
            if (activity != null && activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}

/**
 * Pinch, drag and double-tap on a picture of [content] size (at zoom 1) inside the measured view. A one-finger drag at
 * zoom 1 is left alone so a surrounding pager can swipe; once zoomed, drags pan.
 *
 * @param zoom current state, read on every gesture.
 * @param onZoom receives the new state; [animate] true for a double tap, which callers may tween.
 */
fun Modifier.zoomable(
    zoom: () -> Zoom,
    content: () -> Size,
    maxScale: Float,
    onZoom: (Zoom, animate: Boolean) -> Unit,
): Modifier = this
    .pointerInput(maxScale) {
        detectTapGestures(onDoubleTap = { tap ->
            val view = Size(size.width.toFloat(), size.height.toFloat())
            val c = Offset(tap.x - view.width / 2f, tap.y - view.height / 2f)
            onZoom(doubleTapZoom(zoom(), c.x, c.y, view, content(), maxScale = maxScale), true)
        })
    }
    .pointerInput(maxScale) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var moved = false
            var zoomMotion = 0f
            var panMotion = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.none { it.isConsumed }) {
                    val z = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (!moved) {
                        zoomMotion += abs(1 - z)
                        panMotion += pan.getDistance()
                        moved = zoomMotion > 0.02f || panMotion > slop
                    }
                    val current = zoom()
                    val multi = event.changes.count { it.pressed } > 1
                    if (moved && (multi || current.zoomed)) {
                        val view = Size(size.width.toFloat(), size.height.toFloat())
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val next = zoomBy(current, z, pan.x, pan.y, centroid.x - view.width / 2f, centroid.y - view.height / 2f, view, content(), maxScale)
                        onZoom(next, false)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged() = position != previousPosition
