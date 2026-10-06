package app.cove.companion.data.insights

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Counts started activities so on-device inference can tell whether the app is on screen. */
class ForegroundTracker : Application.ActivityLifecycleCallbacks, ForegroundState {
    @Volatile
    private var started = 0

    /** Called each time the app goes from fully in the background to on screen. */
    @Volatile
    var onEnter: (() -> Unit)? = null

    override fun isForeground() = started > 0

    /** Starts listening to [app]'s activities. */
    fun attach(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) onEnter?.invoke()
    }

    override fun onActivityStopped(activity: Activity) {
        started--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
