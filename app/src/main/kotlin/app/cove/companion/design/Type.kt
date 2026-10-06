package app.cove.companion.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.cove.companion.R

/** Geist variable font; one entry per weight the design uses. */
@OptIn(ExperimentalTextApi::class)
val Geist = FontFamily(
    listOf(300, 400, 500, 600).map { w ->
        Font(
            R.font.geist,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, line: Number, weight: Int = 400, spacing: Double = 0.0) = TextStyle(
    fontFamily = Geist,
    fontSize = size.sp,
    lineHeight = line.toFloat().sp,
    fontWeight = FontWeight(weight),
    letterSpacing = spacing.sp,
    lineHeightStyle = Trim,
)

/** Type scale. Line height is 1.35x unless the design sets it; figures are tabular (see [CoveText]). */
@Immutable
object CoveType {
    val Figure = style(60, 62, 400, -2.4)
    val Hero = style(44, 46, 400, -1.6)
    val Title = style(32, 38, 500, -0.8)
    val Section = style(26, 30, 500, -0.5)
    val Heading = style(19, 25.65, 500, -0.2)
    val Value = style(22, 29.7, 500)
    val Body = style(17, 22.95)
    val BodyMedium = style(17, 22.95, 500)
    val Button = style(15, 20.25, 500)
    val Meta = style(14, 18.9)
    val MetaMedium = style(14, 18.9, 500)
    val Label = style(11, 14.85, 500, 0.1)
}
