package app.cove.companion.design.illustrations

import kotlin.math.max
import kotlin.random.Random

/** One short brush stroke of painted texture, in virtual units. [angle] is in degrees (-90 points up). */
internal class StrokeSpec(
    val x: Float, val y: Float, val len: Float, val angle: Float, val bend: Float,
    val tone: Float, val alpha: Float, val width: Float,
)

/** A tiny speckle dot. */
internal class DotSpec(val x: Float, val y: Float, val r: Float, val tone: Float, val alpha: Float)

/**
 * Deterministic brush strokes inside a box: the same [seed] always yields the same list, so art never shimmers
 * between renders and tests can pin it down.
 *
 * @param angle mean direction in degrees; each stroke deviates by up to +-[jitter].
 */
internal fun strokeField(
    seed: Long, count: Int, left: Float, top: Float, right: Float, bottom: Float,
    len: ClosedFloatingPointRange<Float>, angle: Float, jitter: Float = 24f,
    width: ClosedFloatingPointRange<Float> = 0.8f..1.8f, alpha: ClosedFloatingPointRange<Float> = 0.2f..0.5f,
): List<StrokeSpec> {
    val r = Random(seed)
    fun between(range: ClosedFloatingPointRange<Float>) = range.start + r.nextFloat() * (range.endInclusive - range.start)
    return List(max(count, 0)) {
        StrokeSpec(
            x = left + r.nextFloat() * (right - left), y = top + r.nextFloat() * (bottom - top),
            len = between(len), angle = angle + (r.nextFloat() * 2f - 1f) * jitter, bend = (r.nextFloat() * 2f - 1f) * 0.35f,
            tone = r.nextFloat(), alpha = between(alpha), width = between(width),
        )
    }
}

/** Deterministic speckle dots inside a box. */
internal fun dotField(
    seed: Long, count: Int, left: Float, top: Float, right: Float, bottom: Float, radius: ClosedFloatingPointRange<Float> = 0.4f..1.1f,
): List<DotSpec> {
    val r = Random(seed)
    return List(max(count, 0)) {
        DotSpec(
            left + r.nextFloat() * (right - left), top + r.nextFloat() * (bottom - top),
            radius.start + r.nextFloat() * (radius.endInclusive - radius.start), r.nextFloat(), 0.12f + r.nextFloat() * 0.3f,
        )
    }
}
