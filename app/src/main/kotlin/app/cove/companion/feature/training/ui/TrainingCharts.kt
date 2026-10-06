package app.cove.companion.feature.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import app.cove.companion.design.Cove
import app.cove.companion.design.Geist
import app.cove.companion.feature.training.engine.Axis
import app.cove.companion.feature.training.engine.ChartMath

/** One x label under a chart: the point at [index] is captioned [text]. */
data class ChartLabel(val index: Int, val text: String)

private const val W = 342f
private const val RIGHT = 308f

/**
 * Quiet line chart drawn like the frames' SVG (viewBox 342x150): ink line and soft fill, hollow dots, a solid last
 * dot, a few grey grid lines with their numbers on the right, and first/middle/last captions. Points are evenly spaced.
 *
 * @param summary spoken description for screen readers (the chart itself is not focusable content).
 */
@Composable
fun LineChart(values: List<Double>, axis: Axis, labels: List<ChartLabel>, summary: String, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxWidth().aspectRatio(W / 150f).semantics { contentDescription = summary }) {
        val s = size.width / W
        val label = TextStyle(fontFamily = Geist, fontSize = TextUnit(11f * s / density, TextUnitType.Sp), color = c.tail)
        fun y(v: Double) = ((8 + (1 - axis.fraction(v)) * 118) * s).toFloat()
        axis.ticks.forEach { t ->
            drawLine(c.ink.copy(alpha = 0.09f), Offset(0f, y(t)), Offset(RIGHT * s, y(t)), strokeWidth = s)
            text(measurer, ChartMath.tickLabel(t), label, W * s, y(t), anchorEnd = true, centerY = true)
        }
        if (values.isEmpty()) return@Canvas
        val xs = values.indices.map { if (values.size == 1) RIGHT * s else it * RIGHT * s / (values.size - 1) }
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(xs[i], y(v)) else lineTo(xs[i], y(v)) } }
        if (values.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(xs.last(), 126f * s)
                lineTo(xs.first(), 126f * s)
                close()
            }
            drawPath(area, c.ink.copy(alpha = 0.04f))
            drawPath(line, c.ink, style = Stroke(1.75f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            drawCircle(if (last) c.ink else c.card, (if (last) 4f else 2.5f) * s, Offset(xs[i], y(v)))
            drawCircle(c.ink, (if (last) 4f else 2.5f) * s, Offset(xs[i], y(v)), style = Stroke(1.5f * s))
        }
        labels.forEachIndexed { k, l ->
            val x = xs.getOrNull(l.index) ?: return@forEachIndexed
            when {
                k == 0 -> text(measurer, l.text, label, x, 144f * s, baseline = true)
                k == labels.lastIndex -> text(measurer, l.text, label, x, 144f * s, anchorEnd = true, baseline = true)
                else -> text(measurer, l.text, label, x, 144f * s, center = true, baseline = true)
            }
        }
    }
}

/** One bar of [BarChart]. */
data class Bar(val value: Int, val label: String)

/**
 * Sessions per week (or day, or month) as grey bars with the latest in ink, scaled so [goal] fills the height
 * (frame 48, viewBox 342x110).
 */
@Composable
fun BarChart(bars: List<Bar>, goal: Int, summary: String, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxWidth().aspectRatio(W / 110f).semantics { contentDescription = summary }) {
        val s = size.width / W
        val label = TextStyle(fontFamily = Geist, fontSize = TextUnit(11f * s / density, TextUnitType.Sp), color = c.tail)
        val full = maxOf(goal, bars.maxOfOrNull { it.value } ?: 1, 1)
        val barW = minOf(28f, W / (bars.size * 1.7f))
        bars.forEachIndexed { i, b ->
            val x = if (bars.size == 1) W - barW else i * (W - barW) / (bars.size - 1)
            val h = 82f * b.value / full
            if (b.value > 0) {
                drawRoundRect(
                    if (i == bars.lastIndex) c.ink else c.ink.copy(alpha = 0.16f),
                    Offset(x * s, (88f - h) * s), Size(barW * s, h * s), CornerRadius(6f * s),
                )
            } else {
                drawRoundRect(c.ink.copy(alpha = 0.06f), Offset(x * s, 84f * s), Size(barW * s, 4f * s), CornerRadius(2f * s))
            }
            val cx = (x + barW / 2) * s
            when (i) {
                0 -> text(measurer, b.label, label, x * s, 104f * s, baseline = true)
                bars.lastIndex -> text(measurer, b.label, label, W * s, 104f * s, anchorEnd = true, baseline = true)
                else -> text(measurer, b.label, label, cx, 104f * s, center = true, baseline = true)
            }
        }
    }
}

private fun DrawScope.text(
    m: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float,
    anchorEnd: Boolean = false, center: Boolean = false, centerY: Boolean = false, baseline: Boolean = false,
) {
    val r = m.measure(text, style, softWrap = false)
    val left = when {
        anchorEnd -> x - r.size.width
        center -> x - r.size.width / 2f
        else -> x
    }
    val top = when {
        centerY -> y - r.size.height / 2f
        baseline -> y - r.firstBaseline
        else -> y
    }
    drawText(r, topLeft = Offset(left, top))
}
