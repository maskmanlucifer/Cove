package app.cove.companion.data.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-device assistant switches in plain `SharedPreferences`; not synced, because what a phone downloads and runs is
 * a property of that phone.
 */
class AssistantPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _readDetails = MutableStateFlow(prefs.getBoolean(READ_DETAILS, true))

    /**
     * Whether Cove reads dates, amounts, phone numbers and the like out of text no rule understood, using a small model
     * on this phone (it downloads once, on Wi-Fi). On by default.
     */
    val readDetails: StateFlow<Boolean> = _readDetails

    fun setReadDetails(on: Boolean) {
        prefs.edit().putBoolean(READ_DETAILS, on).apply()
        _readDetails.value = on
    }

    private companion object {
        const val FILE = "assistant_prefs"
        const val READ_DETAILS = "read_details"
    }
}
