package app.cove.companion

import android.app.Application
import android.content.Context
import app.cove.companion.core.Notifications
import app.cove.companion.feature.alarms.AlarmRescheduler
import app.cove.companion.data.auth.GoogleSignIn
import app.cove.companion.feature.onboarding.SignIn
import app.cove.companion.feature.brief.BriefScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        SignIn.launcher = GoogleSignIn(container.auth, BuildConfig.GOOGLE_WEB_CLIENT_ID, container.appScope)
        container.foreground.onEnter = container.sync::onForeground
        container.sync.start()
        container.driveKit.start()
        CoroutineScope(Dispatchers.Default).launch {
            container.settings.settings.map { it.briefOn to it.wakeMinutes }.distinctUntilChanged()
                .collect { (on, wake) -> BriefScheduler.apply(this@CoveApp, on, wake) }
        }
    }
}

/** Shortcut for composables and view models that need the dependency graph. */
val Context.container: AppContainer get() = (applicationContext as CoveApp).container
