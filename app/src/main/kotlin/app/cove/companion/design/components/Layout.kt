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
    Box(modifier.fillMaxSize().background(Cove.colors.canvas), content = content)
}

/** Height of the bottom bar's hit area and fade: pill and orb (44 dp) sit 22 dp above the screen edge. */
val DockBarHeight = 88.dp

/** Space reserved at the bottom so scrolling content clears the bottom bar. */
val DockClearance = 96.dp

/** Bottom offset for controls that float just above the bar (undo bars, add buttons, day pill). */
val DockFloatBottom = 84.dp
