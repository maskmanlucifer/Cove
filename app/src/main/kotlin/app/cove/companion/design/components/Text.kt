package app.cove.companion.design.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType

/** Alpha as a plain modifier (used by press feedback). */
fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier = this.graphicsLayer { this.alpha = alpha }

/** Text with tabular figures; colour defaults to ink. */
@Composable
fun CoveText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CoveType.Body,
    color: Color = Cove.colors.ink,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val box = rememberLineBox(style)
    BasicText(
        text,
        modifier.then(box.modifier),
        style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified, fontFeatureSettings = "tnum"),
        onTextLayout = box.onTextLayout,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Text with a muted trailing part, e.g. "11:00" + " am" or a headline + grey continuation. */
@Composable
fun CoveText(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CoveType.Body,
    color: Color = Cove.colors.ink,
    secondaryColor: Color = Cove.colors.tail,
    textAlign: TextAlign? = null,
) {
    val annotated: AnnotatedString = buildAnnotatedString {
        append(primary)
        withStyle(SpanStyle(color = secondaryColor)) { append(secondary) }
    }
    val box = rememberLineBox(style)
    BasicText(
        annotated,
        modifier.then(box.modifier),
        style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified, fontFeatureSettings = "tnum"),
        onTextLayout = box.onTextLayout,
    )
}
