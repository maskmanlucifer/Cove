package app.cove.companion.design.components

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs

/**
 * Pure geometry and timing of the bottom bar's open/close motion, in dp. Slot centres never change between the two
 * states: the current tab's icon sits at [Geometry.iconCenter] collapsed and expanded, so nothing jumps.
 */
object DockMotion {
    /** One coordinated duration for every part, opening and closing. */
    const val DURATION_MS = 240

    /** Ease-out, no overshoot. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Tabs from this index on get their label to the left of the icon, so the pill never runs into the orb. */
    const val LEFT_EXTENDING_FROM = 3

    const val SLOT_SPACING = 52f
    const val ROW_WIDTH = 4 * SLOT_SPACING + 46f
    private const val ORB_ZONE = 28f + 44f + 8f

    /** Positions for one tab at a given screen width. */
    class Geometry(val expandedLeft: Float, val expandedRight: Float, val collapsedLeft: Float, val collapsedRight: Float, val iconCenter: Float, private val firstCenter: Float) {
        /** Centre x of slot [index] in the open row. */
        fun slotCenter(index: Int): Float = firstCenter + SLOT_SPACING * index
    }

    /** Where everything sits for [selected] on a [screenWidth] dp screen with a label [labelWidth] dp wide. */
    fun geometry(screenWidth: Float, selected: Int, labelWidth: Float): Geometry {
        val left = (screenWidth - ORB_ZONE - ROW_WIDTH) / 2f
        val first = left + 23f
        val cx = first + SLOT_SPACING * selected
        val extent = 10f + 8f + labelWidth + 8f + 14f + 6f
        return if (selected < LEFT_EXTENDING_FROM) {
            Geometry(left, left + ROW_WIDTH, cx - 23f, cx + extent + 16f, cx, first)
        } else {
            Geometry(left, left + ROW_WIDTH, cx - extent - 16f, cx + 23f, cx, first)
        }
    }

    /** What to draw at animation progress [p] (0 collapsed, 1 open). */
    data class Frame(val geom: Float, val otherAlpha: Float, val labelAlpha: Float)

    /** With [reduce], geometry snaps at the midpoint and everything else is a plain fade. */
    fun frame(p: Float, reduce: Boolean): Frame {
        val t = p.coerceIn(0f, 1f)
        if (reduce) {
            return Frame(if (t < 0.5f) 0f else 1f, if (t < 0.5f) 0f else 2f * t - 1f, if (t < 0.5f) 1f - 2f * t else 0f)
        }
        return Frame(t, ((t - 0.35f) / 0.65f).coerceIn(0f, 1f), (1f - t / 0.35f).coerceIn(0f, 1f))
    }

    /** Linear interpolation. */
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** True when the two icon rows overlap the orb zone, used by tests as a layout guard. */
    fun fits(screenWidth: Float, selected: Int, labelWidth: Float): Boolean {
        val g = geometry(screenWidth, selected, labelWidth)
        return g.collapsedLeft >= 0f && maxOf(g.collapsedRight, g.expandedRight) <= screenWidth - 28f - 44f - 4f && abs(g.iconCenter - g.slotCenter(selected)) < 0.01f
    }
}
