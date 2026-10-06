package app.cove.companion.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Accessor for the active [CoveColors]. */
object Cove {
    val colors: CoveColors
        @Composable @ReadOnlyComposable get() = LocalCoveColors.current
}

/**
 * Applies light or dark tokens; follows the system unless [dark] is set.
 *
 * @param textScale multiplier on top of the system font scale (Me, "Look and text size").
 * @param reduceMotion swaps slides for short fades; read through [LocalReduceMotion].
 */
@Composable
fun CoveTheme(
    dark: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalCoveColors provides if (dark) DarkColors else LightColors,
        LocalDensity provides Density(base.density, base.fontScale * textScale),
        LocalReduceMotion provides reduceMotion,
    ) {
        content()
    }
}
