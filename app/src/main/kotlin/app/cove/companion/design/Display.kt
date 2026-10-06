package app.cove.companion.design

import androidx.compose.runtime.staticCompositionLocalOf

/** True when transitions should be short fades instead of slides (see [resolveReduceMotion]). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Duration of the fade that replaces movement when motion is reduced. */
const val ReducedMotionMillis = 150

/** Text-size choices offered in Me: label and the multiplier applied on top of the system font scale. */
object TextScales {
    val options: List<Pair<String, Float>> = listOf("Small" to 0.9f, "Default" to 1f, "Large" to 1.15f, "Largest" to 1.3f)

    /** Index of the option closest to a stored [scale]. */
    fun indexOf(scale: Float): Int = options.indices.minBy { kotlin.math.abs(options[it].second - scale) }

    fun label(scale: Float): String = options[indexOf(scale)].first
}

/**
 * Resolves the stored `reduceMotion` setting (`system` | `on` | `off`) to a boolean.
 *
 * @param systemAnimationsOff whether the system animator scale is zero.
 */
fun resolveReduceMotion(setting: String, systemAnimationsOff: Boolean): Boolean = when (setting) {
    "on" -> true
    "off" -> false
    else -> systemAnimationsOff
}
