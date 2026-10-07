package app.cove.companion

import android.app.Application
import android.content.Context
import app.cove.companion.core.Notifications
import app.cove.companion.feature.alarms.AlarmRescheduler
import app.cove.companion.feature.onboarding.SignInLauncher
import app.cove.companion.feature.onboarding.SignIn
import app.cove.companion.feature.brief.BriefScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.cove.companion.feature.nudges.NudgeScheduler
import app.cove.companion.feature.widgets.WidgetUpdater
import app.cove.companion.data.wipe.DeviceWipe
import app.cove.companion.feature.recovery.PendingRestore
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.resilience.CrashLoop
import app.cove.companion.resilience.DbCheck

/** Application entry point; holds the [AppContainer]. */
class CoveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this, BuildConfig.VERSION_NAME)
        // A requested "Clear all data" finishes here, before anything can open the database.
        if (DeviceWipe.isMainProcess(this)) DeviceWipe.processAtStart(this)
        if (BuildConfig.DEBUG) DebugStrictMode.install()
        val crashLoop = CrashLoop.isLooping(CrashHandler.storeFor(this).all(), System.currentTimeMillis())
        container = AppContainer(this)
        container.foreground.attach(this)
        container.foreground.onEnter = {
            container.appScope.launch { container.sync.onForeground() }
            container.smsCatchUp.runIfDue()
        }
        // Everything below touches Room, WorkManager, the Keystore or AlarmManager: keep it off the main thread
        // so the first frame is not delayed. Alarms, nudges and widgets are registered within moments of launch.
        container.appScope.launch(Dispatchers.IO) {
            val check = container.prepareDatabase(crashLoop) { PendingRestore.applyIfStaged(this@CoveApp, container) }
            // Services touch the database: they start only when it opened and the last launches did not crash-loop.
            if (check == DbCheck.Ok && !crashLoop) startServices()
        }
    }

    /** Starts each background service on its own, so one failing never prevents (or crashes) the others. */
    private suspend fun startServices() {
        val c = container
        CrashHandler.guarded("start:settings") { c.settings.settings.first() }
        CrashHandler.guarded("start:channels") { Notifications.createChannels(this) }
        CrashHandler.guarded("start:alarms") { AlarmRescheduler(this, c).start() }
        CrashHandler.guarded("start:signin") { SignIn.launcher = SignInLauncher { context, onResult -> c.cloud.signIn.signIn(context, onResult) } }
        CrashHandler.guarded("start:cloud") { c.startCloud() }
        CrashHandler.guarded("start:brief") {
            c.appScope.launch {
                c.settings.settings.map { it.briefOn to it.wakeMinutes }.distinctUntilChanged()
                    .collect { (on, wake) -> BriefScheduler.apply(this@CoveApp, on, wake) }
            }
        }
        CrashHandler.guarded("start:training") { app.cove.companion.feature.training.LiveTrainingSummary.install(c) }
        CrashHandler.guarded("start:nudges") { NudgeScheduler(this, c).start(c.appScope) }
        CrashHandler.guarded("start:widgets") { WidgetUpdater.start(this, c, c.appScope) }
    }
}

/** Shortcut for composables and view models that need the dependency graph. */
val Context.container: AppContainer get() = (applicationContext as CoveApp).container
