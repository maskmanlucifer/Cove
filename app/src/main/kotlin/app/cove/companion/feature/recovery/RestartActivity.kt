package app.cove.companion.feature.recovery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Lives in the `:restart` process: opens the main screen again, ends the old Cove process, then itself, so the
 * fresh start never races with the dying one.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?.let(::startActivity)
        val old = intent.getIntExtra(EXTRA_PID, -1)
        if (old > 0) Process.killProcess(old)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /** Process id of the Cove process to end. */
        const val EXTRA_PID = "pid"
    }
}
