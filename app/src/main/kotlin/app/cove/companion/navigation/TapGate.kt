package app.cove.companion.navigation

/**
 * Drops repeated navigation requests for the same [key] within [windowMs], so a double tap on a row or "+" opens
 * one screen, not two.
 */
class TapGate(private val windowMs: Long = 500, private val now: () -> Long = System::currentTimeMillis) {
    private var lastKey: String? = null
    private var lastAt = 0L

    /** True when the request should go ahead. */
    fun allow(key: String): Boolean {
        val t = now()
        if (key == lastKey && t - lastAt < windowMs) return false
        lastKey = key
        lastAt = t
        return true
    }
}
