package app.cove.companion.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws [icon] tinted with [tint]; the icon's own colours are ignored. */
@Composable
fun CoveIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Builds a 24x24 stroke icon from SVG path data, matching the design's line icons. */
private fun line(name: String, width: Float = 1.6f, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun filled(name: String, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

/** Rounded rectangle as path data (SVG `rect` with `rx`). */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 -$r,$r " +
        "h-${w - 2 * r} a$r,$r 0 0 1 -$r,-$r v-${h - 2 * r} a$r,$r 0 0 1 $r,-$r z"

/** Circle as path data. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},$cy a$r,$r 0 1 0 ${2 * r},0 a$r,$r 0 1 0 -${2 * r},0 z"

/** Icon set extracted from the design frames. Stroke width follows the design (1.6 default). */
object CoveIcons {
    val Today = line("today", 1.6f, circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2")
    val TodayBold = line("todayBold", 2f, circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2")
    val Plan = line("plan", 1.6f, "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val PlanBold = line("planBold", 2f, "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val Money = line("money", 1.6f, rect(3f, 6f, 18f, 13f, 3f), "M3 10h18")
    val MoneyBold = line("moneyBold", 2f, rect(3f, 6f, 18f, 13f, 3f), "M3 10h18")
    val Journal = line("journal", 1.6f, "M6 4h12v16H6z", "M10 9h4")
    val JournalBold = line("journalBold", 2f, "M6 4h12v16H6z", "M10 9h4")
    val Me = line("me", 1.6f, circle(12f, 8f, 4f), "M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5")
    val MeBold = line("meBold", 2f, circle(12f, 8f, 4f), "M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5")
    val Mic = line("mic", 1.7f, rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Close = line("close", 1.8f, "M6 6l12 12M18 6L6 18")
    val Plus = line("plus", 1.8f, "M12 5v14M5 12h14")
    val Check = line("check", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val ChevronRight = line("chevronRight", 2f, "M9 6l6 6-6 6")
    val ChevronLeft = line("chevronLeft", 1.6f, "M15 5l-7 7 7 7")
    val ChevronDown = line("chevronDown", 2f, "M6 9l6 6 6-6")
    val Backspace = line("backspace", 1.6f, "M9 6h11v12H9l-5-6z", "M12.5 10l4 4M16.5 10l-4 4")
    val Grip = filled(
        "grip",
        circle(9f, 7f, 1.4f), circle(15f, 7f, 1.4f), circle(9f, 12f, 1.4f),
        circle(15f, 12f, 1.4f), circle(9f, 17f, 1.4f), circle(15f, 17f, 1.4f),
    )
    val Previous = filled("previous", "M11 6v12L3 12zM20 6v12l-8-6z")
    val Next = filled("next", "M13 6v12l8-6zM4 6v12l8-6z")
    val Pause = filled("pause", rect(6f, 5f, 4f, 14f, 1.2f), rect(14f, 5f, 4f, 14f, 1.2f))
    val Play = filled("play", "M8 5v14l11-7z")
    val Wifi = line("wifi", 2f, "M2 8.5a15 15 0 0 1 20 0M5.5 12a10 10 0 0 1 13 0M9 15.5a5 5 0 0 1 6 0", "M12 19h.01")
}
