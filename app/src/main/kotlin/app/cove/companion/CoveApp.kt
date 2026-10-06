package app.cove.companion

import android.app.Application
import android.content.Context

/** Application entry point; holds the [AppContainer]. */
class CoveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Shortcut for composables and view models that need the dependency graph. */
val Context.container: AppContainer get() = (applicationContext as CoveApp).container
