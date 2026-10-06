package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/** Visual weight of a [PillButton]. */
enum class ButtonKind { Primary, Secondary, Text, Destructive }

/**
 * Pill button: ink fill for [ButtonKind.Primary], tinted fill for Secondary (override with [container]),
 * and plain text for Text/Destructive.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    height: Dp = 44.dp,
    horizontalPadding: Dp = 18.dp,
    container: Color? = null,
    textStyle: TextStyle? = null,
) {
    val c = Cove.colors
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.ink to c.onInk
        ButtonKind.Secondary -> (container ?: c.well) to c.ink
        ButtonKind.Text -> Color.Transparent to c.ink
        ButtonKind.Destructive -> Color.Transparent to c.alert
    }
    Box(
        modifier
            .heightIn(min = height)
            .background(bg, CoveShapes.Pill)
            .pressable(onClick, role = Role.Button)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(
            text,
            style = textStyle ?: if (kind == ButtonKind.Secondary) CoveType.Button.copy(fontWeight = FontWeight.Normal) else CoveType.Button,
            color = fg,
        )
    }
}

/** Click with the design's press feel (no ripple, dims to 96% for 100 ms). */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState().value
    return this
        .graphicsLayerAlpha(if (pressed) 0.96f else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/**
 * Icon-only button semantics: spoken [label] and a button role. Apply before [pressable] on the clickable
 * node, or use [iconButton] to do both.
 */
@Composable
fun Modifier.iconButton(label: String, onClick: () -> Unit): Modifier =
    this.tapTarget().pressable(onClick, role = Role.Button).semantics { contentDescription = label }

/** Minimum 48dp tap target around small icons. */
fun Modifier.tapTarget(): Modifier = this.defaultMinSize(48.dp, 48.dp)
