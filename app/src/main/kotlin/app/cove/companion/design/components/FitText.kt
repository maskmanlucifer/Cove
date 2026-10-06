package app.cove.companion.design.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType

/**
 * Largest scale in [minScale]..1 (in 5% steps) at which text of width `measure(scale)` fits in [available].
 * Falls back to [minScale] when even that does not fit.
 */
internal fun fitScale(available: Int, minScale: Float, measure: (Float) -> Int): Float {
    var scale = 1f
    while (scale > minScale) {
        if (measure(scale) <= available) return scale
        scale -= 0.05f
    }
    return minScale
}

private fun TextUnit.scaled(by: Float): TextUnit = if (isSp) (value * by).sp else this

/**
 * One line of text that shrinks (down to [minScale]) instead of wrapping when it does not fit, so a huge amount
 * keeps its layout. [secondary] is drawn after [text] in the muted trailing colour.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CoveType.Body,
    color: Color = Cove.colors.ink,
    secondary: String = "",
    minScale: Float = 0.4f,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val scale = remember(text, secondary, style, available) {
            fitScale(available, minScale) { s ->
                measurer.measure(text + secondary, style.copy(fontSize = style.fontSize.scaled(s), letterSpacing = style.letterSpacing.scaled(s)), maxLines = 1, softWrap = false).size.width
            }
        }
        val fitted = style.copy(
            fontSize = style.fontSize.scaled(scale),
            lineHeight = style.lineHeight.scaled(scale),
            letterSpacing = style.letterSpacing.scaled(scale),
        )
        if (secondary.isEmpty()) CoveText(text, style = fitted, color = color, maxLines = 1)
        else CoveText(text, secondary, style = fitted, color = color)
    }
}
