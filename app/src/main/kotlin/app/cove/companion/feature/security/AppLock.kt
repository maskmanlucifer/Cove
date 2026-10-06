package app.cove.companion.feature.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How long the app may stay in the background before the lock returns; [key] is the stored setting value. */
enum class LockAfter(val key: String, val label: String, val millis: Long) {
    Immediately("immediately", "Immediately", 0),
    OneMinute("1min", "1 minute", 60_000),
    FiveMinutes("5min", "5 minutes", 300_000),
    ;

    companion object {
        /** The option stored as [key]; unknown values fall back to one minute. */
        fun fromKey(key: String): LockAfter = entries.firstOrNull { it.key == key } ?: OneMinute
    }
}

/**
 * Locked/unlocked state of the app's UI. The process starts locked, so every cold start asks to unlock; going to
 * the background for at least the chosen [LockAfter] locks it again. The database key is deliberately independent
 * of this state so alarms, widgets and workers keep working while locked.
 *
 * @param now monotonic milliseconds (not the app clock, which debug builds freeze).
 */
class AppLock(private val now: () -> Long) {
    private val _locked = MutableStateFlow(true)
    private var enabled: Boolean? = null
    private var after = LockAfter.OneMinute
    private var backgroundedAt: Long? = null

    /** True while the lock screen must cover the app (only meaningful when the setting is on). */
    val locked: StateFlow<Boolean> = _locked

    /**
     * Applies the user's settings. Turning the lock on from a known "off" counts as unlocked, because turning it on
     * requires authenticating; the first call after launch keeps the cold-start lock.
     */
    @Synchronized
    fun configure(enabled: Boolean, after: LockAfter) {
        if (this.enabled == false && enabled) unlock()
        this.enabled = enabled
        this.after = after
    }

    /** Marks the app unlocked after a successful authentication. */
    @Synchronized
    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    /** Locks right now. */
    @Synchronized
    fun lock() {
        _locked.value = true
        backgroundedAt = null
    }

    /** Call when the UI leaves the foreground. */
    @Synchronized
    fun onBackground() {
        if (!_locked.value && backgroundedAt == null) backgroundedAt = now()
    }

    /** Call when the UI returns; locks if it was away for at least the configured time. */
    @Synchronized
    fun onForeground() {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled == true && now() - at >= after.millis) _locked.value = true
    }
}
