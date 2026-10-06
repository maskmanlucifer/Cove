package app.cove.companion.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis

/**
 * Floating bottom sheet over a dimmed scrim. Rises in 280 ms; tap outside to dismiss.
 * Sits 8 dp above the physical screen bottom and draws behind the gesture bar, as in the frames; the 24 dp bottom padding keeps content clear of the handle.
 * Place at the end of a full-screen `Box` so it overlays the page.
 */
@Composable
fun CoveSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Cove.colors
    val reduce = LocalReduceMotion.current
    val shadow = if (c.isDark) Color(0x66000000) else Color(0x1A141420)
    AnimatedVisibility(visible, enter = fadeIn(tween(280)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        enter = if (reduce) fadeIn(tween(ReducedMotionMillis)) else slideInVertically(tween(280)) { it },
        exit = if (reduce) fadeOut(tween(ReducedMotionMillis)) else slideOutVertically(tween(200)) { it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .shadow(20.dp, CoveShapes.SheetFloating, ambientColor = shadow, spotColor = shadow)
                    .background(c.card, CoveShapes.SheetFloating)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
            ) { content() }
        }
    }
}
