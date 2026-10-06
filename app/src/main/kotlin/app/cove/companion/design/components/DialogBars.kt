package app.cove.companion.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.cove.companion.design.Cove

/**
 * Call inside a `Dialog` so its own window draws status and navigation bar icons for the app theme
 * (light icons in dark mode); a dialog window does not inherit the activity's bar style.
 */
@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val dark = Cove.colors.isDark
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
