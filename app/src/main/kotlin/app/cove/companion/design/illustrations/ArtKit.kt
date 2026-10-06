package app.cove.companion.design.illustrations

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.PathEffect
import app.cove.companion.design.OrbColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Colours a scene is drawn with: the orb palette at low saturation (light) or deeper and quieter (dark). */
@Immutable
class ScenePalette(val dark: Boolean) {
    val peach = if (dark) Color(0xFF8A6A58) else OrbColors.Peach
    val lilac = if (dark) Color(0xFF5E5890) else OrbColors.Lilac
    val sky = if (dark) Color(0xFF3F6A82) else OrbColors.Sky
    val base = if (dark) Color(0xFF2A2833) else OrbColors.Base
    val sage = if (dark) Color(0xFF587F6B) else Color(0xFFB4D0BE)
    val paper = if (dark) Color(0xFF34333E) else Color(0xFFFFFFFF)
    val paperShade = if (dark) Color(0xFF2B2A33) else Color(0xFFF1EFF6)
    val line = if (dark) Color(0xFFEDEDEF).copy(alpha = 0.62f) else Color(0xFF16171A).copy(alpha = 0.68f)
    val faint = if (dark) Color(0xFFEDEDEF).copy(alpha = 0.16f) else Color(0xFF16171A).copy(alpha = 0.13f)
    val shadow = if (dark) Color(0xFF000000).copy(alpha = 0.30f) else Color(0xFF3A3550).copy(alpha = 0.09f)
    val glow = if (dark) Color(0xFFF2C4A0) else Color(0xFFFFD9B8)
    val night = if (dark) Color(0xFF15141F) else Color(0xFF2E2C4A)
    val nightHi = if (dark) Color(0xFF27264A) else Color(0xFF55527F)
    val moon = if (dark) Color(0xFFE6E2F2) else Color(0xFFF6F2FF)
}

internal sealed class Op

internal class FillOp(val path: Path, val color: Color, val brush: Brush?, val alpha: Float) : Op()

internal class LineOp(val path: Path, val color: Color, val width: Float, val dashed: Boolean, val alpha: Float) : Op() {
    private var cachedUnit = -1f
    private var cached: Stroke? = null
    fun stroke(unit: Float): Stroke {
        if (cached == null || cachedUnit != unit) {
            cachedUnit = unit
            cached = Stroke(
                width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2.5f * unit, 6f * unit)) else null,
            )
        }
        return cached!!
    }
}

/** A group that drifts by (ax, ay) virtual units and breathes by [grow] around (px, py), once per loop. */
internal class MoveOp(
    val ax: Float, val ay: Float, val grow: Float, val px: Float, val py: Float, val shift: Float,
    val ops: List<Op>,
) : Op()

/** A finished scene: virtual size plus draw operations, built once and replayed on every draw. */
internal class Art(val width: Float, val height: Float, val ops: List<Op>, val animated: Boolean)

/** Collects operations in virtual coordinates; widths are in dp so lines stay 1.5 px thin at any size. */
internal class ArtBuilder(val pal: ScenePalette) {
    private var ops = mutableListOf<Op>()
    private var animated = false

    fun fill(path: Path, color: Color, alpha: Float = 1f) { ops += FillOp(path, color, null, alpha) }

    fun fill(path: Path, brush: Brush, alpha: Float = 1f) { ops += FillOp(path, Color.Unspecified, brush, alpha) }

    fun line(path: Path, width: Float = 1.6f, alpha: Float = 1f, color: Color = pal.line, dashed: Boolean = false) {
        ops += LineOp(path, color, width, dashed, alpha)
    }

    /** Drifts or breathes everything drawn in [block]. */
    fun move(
        ax: Float = 0f, ay: Float = 0f, grow: Float = 0f, px: Float = 0f, py: Float = 0f, shift: Float = 0f,
        block: ArtBuilder.() -> Unit,
    ) {
        val outer = ops
        ops = mutableListOf()
        block()
        outer += MoveOp(ax, ay, grow, px, py, shift, ops)
        ops = outer
        animated = true
    }

    fun build(width: Float, height: Float) = Art(width, height, ops, animated)

    /** Soft orb-gradient glow (peach, lilac, sky) used as the quiet backdrop of a scene. */
    fun backdrop(cx: Float, cy: Float, r: Float, strength: Float = if (pal.dark) 0.5f else 0.85f) {
        val area = oval(cx, cy, r * 1.6f, r * 1.6f)
        fill(area, radial(cx + r * 0.1f, cy + r * 0.25f, r, pal.sky), strength * 0.9f)
        fill(area, radial(cx + r * 0.28f, cy - r * 0.1f, r * 0.8f, pal.lilac), strength)
        fill(area, radial(cx - r * 0.25f, cy - r * 0.2f, r * 0.75f, pal.peach), strength)
    }

    fun shadow(cx: Float, cy: Float, rx: Float, ry: Float = rx * 0.14f) = fill(oval(cx, cy, rx, ry), pal.shadow)
}

internal fun radial(cx: Float, cy: Float, r: Float, color: Color, a: Float = 1f): Brush =
    Brush.radialGradient(0f to color.copy(alpha = a), 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = r)

internal fun vertical(y0: Float, y1: Float, top: Color, bottom: Color): Brush =
    Brush.linearGradient(0f to top, 1f to bottom, start = Offset(0f, y0), end = Offset(0f, y1))

internal fun oval(cx: Float, cy: Float, rx: Float, ry: Float = rx) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

internal fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) =
    Path().apply { addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad))) }

internal inline fun path(block: Path.() -> Unit) = Path().apply(block)

/** Leaf with its base at (bx, by) pointing [deg] degrees (0 = right, -90 = up), [len] long and [wid] wide. */
internal fun leaf(bx: Float, by: Float, deg: Float, len: Float, wid: Float): Path {
    val a = deg * PI.toFloat() / 180f
    val ca = cos(a); val sa = sin(a)
    fun p(x: Float, y: Float) = Offset(bx + x * ca - y * sa, by + x * sa + y * ca)
    val c1 = p(len * 0.35f, -wid); val c2 = p(len * 0.75f, -wid * 0.5f); val tip = p(len, 0f)
    val c3 = p(len * 0.75f, wid * 0.5f); val c4 = p(len * 0.35f, wid)
    return path {
        moveTo(bx, by)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, bx, by)
        close()
    }
}

/** Cloud silhouette from overlapping circles on a flat base, [w] wide, centred on [cx] with its base at [base]. */
internal fun cloud(cx: Float, base: Float, w: Float): Path {
    val h = w * 0.3f
    return path {
        addRoundRect(RoundRect(cx - w / 2, base - h, cx + w / 2, base, CornerRadius(h / 2)))
        addOval(Rect(cx - w * 0.30f, base - h * 1.75f, cx + w * 0.06f, base - h * 0.15f))
        addOval(Rect(cx - w * 0.08f, base - h * 2.15f, cx + w * 0.30f, base - h * 0.15f))
    }
}

internal fun DrawScope.drawOps(ops: List<Op>, unit: Float, t: Float) {
    for (op in ops) when (op) {
        is FillOp -> if (op.brush != null) drawPath(op.path, op.brush, op.alpha) else drawPath(op.path, op.color, op.alpha)
        is LineOp -> drawPath(op.path, op.color, op.alpha, op.stroke(unit))
        is MoveOp -> {
            val k = sin(2f * PI.toFloat() * (t + op.shift))
            translate(op.ax * k, op.ay * k) {
                if (op.grow != 0f) scale(1f + op.grow * k, 1f + op.grow * k, Offset(op.px, op.py)) { drawOps(op.ops, unit, t) }
                else drawOps(op.ops, unit, t)
            }
        }
    }
}
