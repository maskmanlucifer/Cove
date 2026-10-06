package app.cove.companion.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/**
 * Makes Compose text occupy exactly `lines x lineHeight` like a CSS line box.
 * Compose keeps the font's natural leading above the first and below the last line when the
 * requested line height is smaller than the font's; this trims that excess.
 */
class LineBox internal constructor(private val lineHeightPx: Float) {
    private var result by mutableStateOf<TextLayoutResult?>(null)

    val onTextLayout: (TextLayoutResult) -> Unit = { result = it }

    val modifier: Modifier = Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val r = result
        val extra = if (r == null) 0 else ((p.height - r.lineCount * lineHeightPx) / 2f).coerceAtLeast(0f).roundToInt()
        layout(p.width, p.height - 2 * extra) { p.place(0, -extra) }
    }
}

/** Remembers a [LineBox] for [style]; apply `.modifier` and pass `.onTextLayout` to the text. */
@Composable
fun rememberLineBox(style: TextStyle): LineBox {
    val px = with(LocalDensity.current) { style.lineHeight.toPx() }
    return remember(px) { LineBox(px) }
}
