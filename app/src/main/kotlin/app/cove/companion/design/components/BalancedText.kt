package app.cove.companion.design.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import app.cove.companion.design.Cove

/**
 * Headline with a muted continuation, wrapped like CSS `text-wrap: balance`: the line count of
 * normal wrapping is kept and breaks are chosen to minimise the squared slack of every line.
 */
@Composable
fun BalancedText(
    primary: String,
    secondary: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Cove.colors.ink,
    secondaryColor: Color = Cove.colors.tail,
) {
    val measurer = rememberTextMeasurer()
    val styled = style.copy(color = color)
    BoxWithConstraints(modifier) {
        val full = primary + secondary
        val text = balance(measurer, full, primary.length, styled, secondaryColor, constraints.maxWidth)
        val box = rememberLineBox(styled)
        BasicText(text, box.modifier, styled, onTextLayout = box.onTextLayout)
    }
}

private fun balance(
    m: TextMeasurer,
    full: String,
    split: Int,
    style: TextStyle,
    secondaryColor: Color,
    maxWidth: Int,
): AnnotatedString {
    fun width(s: String) = m.measure(s, style, softWrap = false, constraints = Constraints()).size.width
    val lineCount = m.measure(full, style, constraints = Constraints(maxWidth = maxWidth)).lineCount
    val words = Regex("\\S+").findAll(full).map { it.range }.toList()
    val n = words.size
    var breaks = emptySet<Int>()
    if (lineCount > 1 && n > 1) {
        fun cost(i: Int, j: Int): Double {
            val w = width(full.substring(words[i].first, words[j].last + 1))
            return if (w > maxWidth) Double.POSITIVE_INFINITY else (maxWidth - w).toDouble().let { it * it }
        }
        // best[k][j]: minimal cost of laying words[0..j] on exactly k+1 lines
        val best = Array(lineCount) { DoubleArray(n) { Double.POSITIVE_INFINITY } }
        val from = Array(lineCount) { IntArray(n) { -1 } }
        for (j in 0 until n) best[0][j] = cost(0, j)
        for (k in 1 until lineCount) for (j in k until n) for (i in k..j) {
            val c = best[k - 1][i - 1] + cost(i, j)
            if (c < best[k][j]) { best[k][j] = c; from[k][j] = i }
        }
        if (best[lineCount - 1][n - 1].isFinite()) {
            val found = mutableSetOf<Int>()
            var j = n - 1
            for (k in lineCount - 1 downTo 1) {
                val i = from[k][j]
                found += words[i].first - 1
                j = i - 1
            }
            breaks = found
        }
    }
    val chars = full.mapIndexed { i, ch -> if (i in breaks) '\n' else ch }.joinToString("")
    return AnnotatedString.Builder(chars).apply {
        if (split < chars.length) addStyle(SpanStyle(color = secondaryColor), split, chars.length)
    }.toAnnotatedString()
}
