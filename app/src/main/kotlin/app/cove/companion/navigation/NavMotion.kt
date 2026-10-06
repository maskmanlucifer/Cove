package app.cove.companion.navigation

import androidx.compose.animation.core.CubicBezierEasing

/**
 * The one set of numbers every screen transition uses: a short fade plus a small slide, same easing, so moving
 * between screens, tabs and overlays feels like one motion. Pure so tests can pin it.
 */
object NavMotion {
    /** Entering screen. */
    const val ENTER_MS = 240

    /** Leaving screen; shorter so the old one is gone before the new one settles. */
    const val EXIT_MS = 160

    /** Tab fade-in. */
    const val TAB_MS = 160

    /** Horizontal travel in dp (forward screens come from the right, back goes the other way). */
    const val SLIDE_DP = 16

    /** Entering/exiting overlays (voice) travel less because they rise from the dock. */
    const val RISE_DP = 24

    /** Ease-out used everywhere. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
