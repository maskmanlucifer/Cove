package app.cove.companion

import android.os.StrictMode

/** Debug-only StrictMode policy: logs (never crashes on) main-thread disk/network work and leaks; filter logcat by `StrictMode`. */
object DebugStrictMode {
    /** Installs thread and VM policies for the process. */
    fun install() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().detectActivityLeaks().penaltyLog().build())
    }
}
