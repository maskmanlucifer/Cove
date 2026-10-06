package app.cove.companion.feature.connect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Puts the setup code on the clipboard as sensitive content and removes it again after a minute. */
object SetupCodeShare {
    private const val LABEL = "Cove setup code"
    private const val CLEAR_AFTER_MS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /**
     * Copies [code] with `ClipDescription.EXTRA_IS_SENSITIVE` (Android hides the preview) and clears the clipboard after
     * 60 seconds if it still holds this code. The sensitive flag alone does not clear anything, so the timer does.
     * The timer lives in the app process; if Android ended it, the clipboard keeps the code until it is replaced.
     */
    fun copy(context: Context, code: String) {
        val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(LABEL, code)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        pending?.let(handler::removeCallbacks)
        val clear = Runnable {
            if (clipboard.primaryClipDescription?.label == LABEL) clipboard.clearPrimaryClip()
            pending = null
        }
        pending = clear
        handler.postDelayed(clear, CLEAR_AFTER_MS)
    }
}
