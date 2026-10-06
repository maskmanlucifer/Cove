package app.cove.companion

import android.app.Application
import android.content.Context
import app.cove.companion.core.Notifications
import app.cove.companion.feature.alarms.AlarmRescheduler
import app.cove.companion.data.auth.GoogleSignIn
import app.cove.companion.feature.onboarding.SignIn
import app.cove.companion.feature.brief.BriefScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.cove.companion.feature.nudges.NudgeScheduler
import app.cove.companion.feature.widgets.WidgetUpdater

/** Application entry point; holds the [AppContainer]. */
class CoveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) DebugStrictMode.install()
        container = AppContainer(this)
        container.foreground.attach(this)
        container.foreground.onEnter = { container.appScope.launch { container.sync.onForeground() } }
        // Everything below touches Room, WorkManager, the Keystore or AlarmManager: keep it off the main thread
        // so the first frame is not delayed. Alarms, nudges and widgets are registered within moments of launch.
        container.appScope.launch(Dispatchers.IO) { startServices() }
    }

    private suspend fun startServices() {
        val c = container
        c.settings.settings.first()
        Notifications.createChannels(this)
        AlarmRescheduler(this, c).start()
        SignIn.launcher = GoogleSignIn(c.auth, BuildConfig.GOOGLE_WEB_CLIENT_ID, c.appScope)
        c.sync.start()
        c.driveKit.start()
        c.appScope.launch {
            c.settings.settings.map { it.briefOn to it.wakeMinutes }.distinctUntilChanged()
                .collect { (on, wake) -> BriefScheduler.apply(this@CoveApp, on, wake) }
        }
        NudgeScheduler(this, c).start(c.appScope)
        WidgetUpdater.start(this, c, c.appScope)
    }
}

/** Shortcut for composables and view models that need the dependency graph. */
val Context.container: AppContainer get() = (applicationContext as CoveApp).container
