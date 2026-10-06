package app.cove.companion.design.illustrations

import kotlin.math.PI
import kotlin.math.sin

/** Milliseconds of one idle loop: the antenna leaf sways once and the mascot blinks twice. */
internal const val IdleLoopMillis = 7000

/** Eyelid closure 0..1 at loop position [t] (0..1): two quick blinks per loop (about every 3.5 s). */
internal fun blinkAt(t: Float): Float {
    val f = t.mod(1f)
    fun blink(centre: Float): Float {
        val d = kotlin.math.abs(f - centre) / 0.0225f
        return if (d >= 1f) 0f else 1f - d
    }
    return maxOf(blink(0.3f), blink(0.8f))
}

/** Antenna sway -1..1 at loop position [t]. */
internal fun swayAt(t: Float): Float = sin(2.0 * PI * t).toFloat()
