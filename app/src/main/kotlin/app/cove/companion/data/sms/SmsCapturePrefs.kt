package app.cove.companion.data.sms

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-device settings of live capture, in plain `SharedPreferences` (no database needed, so the SMS receiver can read
 * the mode before anything else opens). They are not synced: whether this phone reads its messages is a property of
 * this phone and its permissions, not of the user's data. Nothing here is derived from message text.
 */
class SmsCapturePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(CaptureMode.of(prefs.getString(MODE, null)))

    /** The chosen mode; [CaptureMode.Off] until the user chooses. */
    val mode: StateFlow<CaptureMode> = _mode

    /** Saves [mode]. Turning capture on remembers [now], so only messages from then on are picked up automatically. */
    fun setMode(mode: CaptureMode, now: Long) {
        if (_mode.value == CaptureMode.Off && mode != CaptureMode.Off) prefs.edit().putLong(ENABLED_AT, now).apply()
        prefs.edit().putString(MODE, mode.key).apply()
        _mode.value = mode
    }

    /** When capture was last switched on (epoch millis); 0 when never. */
    val enabledAt: Long get() = prefs.getLong(ENABLED_AT, 0L)

    /** Whether the one-time offer on the import screen has been answered. */
    var offerShown: Boolean
        get() = prefs.getBoolean(OFFER_SHOWN, false)
        set(v) { prefs.edit().putBoolean(OFFER_SHOWN, v).apply() }

    /** When the catch-up scan last started (epoch millis); 0 when never. */
    var lastCatchUpAt: Long
        get() = prefs.getLong(LAST_CATCH_UP, 0L)
        set(v) { prefs.edit().putLong(LAST_CATCH_UP, v).apply() }

    private companion object {
        const val FILE = "sms_capture"
        const val MODE = "mode"
        const val ENABLED_AT = "enabled_at"
        const val OFFER_SHOWN = "offer_shown"
        const val LAST_CATCH_UP = "last_catch_up"
    }
}
