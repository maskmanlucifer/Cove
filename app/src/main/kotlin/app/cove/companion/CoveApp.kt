package app.cove.companion

import android.app.Application
import android.content.Context
import app.cove.companion.core.Notifications
import app.cove.companion.feature.alarms.AlarmRescheduler
import app.cove.companion.feature.nudges.NudgeScheduler
import app.cove.companion.feature.widgets.WidgetUpdater

/** Application entry point; holds the [AppContainer]. */
class CoveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.foreground.attach(this)
        Notifications.createChannels(this)
        AlarmRescheduler(this, container).start()
        NudgeScheduler(this, container).start(container.appScope)
        WidgetUpdater.start(this, container, container.appScope)
    }
}

/** Shortcut for composables and view models that need the dependency graph. */
val Context.container: AppContainer get() = (applicationContext as CoveApp).container
