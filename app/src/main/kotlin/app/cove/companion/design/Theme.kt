package app.cove.companion.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Accessor for the active [CoveColors]. */
object Cove {
    val colors: CoveColors
        @Composable @ReadOnlyComposable get() = LocalCoveColors.current
}

/** Applies light or dark tokens; follows the system unless [dark] is set. */
@Composable
fun CoveTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoveColors provides if (dark) DarkColors else LightColors) {
        content()
    }
}
