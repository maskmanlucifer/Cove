package app.cove.companion.feature.alarms

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.components.rememberLineBox

/**
 * A clock time such as "6:30 am" whose am/pm suffix is smaller and greyed, as in the alarm frames.
 * [gap] is the extra space before the suffix in sp-equivalent width.
 */
@Composable
fun TimeLabel(
    minutes: Int,
    style: TextStyle,
    suffixSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Cove.colors.ink,
    suffixColor: Color = Cove.colors.tail,
    gap: TextUnit = 0.sp,
) {
    val t = clockText(minutes)
    val text = buildAnnotatedString {
        append(t.digits)
        withStyle(SpanStyle(fontSize = if (gap.value > 0) gap else suffixSize, letterSpacing = 0.sp)) { append(" ") }
        withStyle(SpanStyle(fontSize = suffixSize, letterSpacing = 0.sp, color = suffixColor)) { append(t.suffix.trim()) }
    }
    val box = rememberLineBox(style)
    BasicText(
        text,
        modifier.then(box.modifier),
        style.copy(color = color, fontFeatureSettings = "tnum"),
        onTextLayout = box.onTextLayout,
    )
}
