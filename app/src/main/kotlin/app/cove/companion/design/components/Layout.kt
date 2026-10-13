package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove

/** Design status-bar band is 48dp; use the real inset when it is taller. */
@Composable
fun Modifier.coveTopInset(min: Dp = 48.dp): Modifier {
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    return this.padding(top = maxOf(top, min))
}

/** Full-screen canvas background; content area starts below the status bar. */
@Composable
fun CoveScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Cove.colors.canvas).paperGrain(if (Cove.colors.isDark) 0.09f else 0.07f), content = content)
}

/** Gap between the screen's bottom edge and the floating dock at the bottom right. */
val DockBottom = 22.dp

/** From the screen's bottom edge to the top of the dock: page dots (64 dp) over the orb (48 dp) in a padded capsule. */
val DockBarHeight = DockBottom + 124.dp

/** Space reserved at the bottom so scrolling content clears the dock. */
val DockClearance = DockBarHeight + 16.dp

/** Bottom offset for controls that float at the bottom left, level with the dock (day pill). */
val DockFloatBottom = DockBottom

/** Bottom offset for a control that floats at the bottom right, stacked just above the dock (add buttons). */
val DockStackBottom = DockBarHeight + 12.dp
