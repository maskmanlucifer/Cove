package app.cove.companion.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cove.companion.AppContainer
import app.cove.companion.container

/** Creates (or restores) a [ViewModel] built from the app's dependency graph. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
