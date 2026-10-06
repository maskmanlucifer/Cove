package app.cove.companion.feature.training.engine

import kotlin.math.ceil
import kotlin.math.floor

/** Y axis of a quiet line chart: [ticks] from low to high; the data fits between the first and the last. */
data class Axis(val lo: Double, val hi: Double, val ticks: List<Double>) {
    /** Where [v] falls between [lo] (0) and [hi] (1). */
    fun fraction(v: Double): Double = if (hi == lo) 0.5 else (v - lo) / (hi - lo)
}

/** Chart scale maths for the lift and body weight charts (pure, unit tested). */
object ChartMath {
    private val nice = listOf(0.5, 1.0, 2.5, 5.0, 10.0, 25.0, 50.0, 100.0)

    /**
     * An axis for [values]: the smallest nice step whose two intervals cover the range, the top tick on [grid]
     * above the largest value, and as many steps down as it takes to include the smallest.
     */
    fun axis(values: List<Double>, grid: Double): Axis {
        if (values.isEmpty()) return Axis(0.0, 1.0, listOf(0.0, 1.0))
        val min = values.min()
        val max = values.max()
        val range = max - min
        val step = nice.firstOrNull { range <= 2 * it } ?: nice.last()
        val g = if (grid > 0) grid else step
        val hi = ceil(max / g - 1e-9) * g
        val n = maxOf(1, ceil((hi - min) / step - 1e-9).toInt())
        val lo = hi - n * step
        return Axis(lo, hi, (0..n).map { lo + it * step })
    }

    /** Indexes of the first, middle and last of [count] points, without duplicates (labels under the chart). */
    fun labelIndexes(count: Int): List<Int> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(0)
        count == 2 -> listOf(0, 1)
        else -> listOf(0, floor((count - 1) / 2.0).toInt(), count - 1)
    }

    /** Tick text: whole numbers without decimals, otherwise one decimal ("68.5"). */
    fun tickLabel(v: Double): String = WeightFormat.trim(v)
}
