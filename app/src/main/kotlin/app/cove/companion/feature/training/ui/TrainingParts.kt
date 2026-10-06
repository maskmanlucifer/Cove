package app.cove.companion.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Type used across the training frames. */
object TrainingType {
    val Button = CoveType.Button.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    val Row = CoveType.Body.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    val Big = CoveType.Figure.copy(fontSize = 64.sp, lineHeight = 66.sp)
    val Rest = CoveType.Figure.copy(fontSize = 96.sp, lineHeight = 96.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp)
    val Sub = CoveType.Button.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
}

/** Top row of 56dp: [start] and [end] around a muted centre label. */
@Composable
fun TrainingTopBar(label: String, start: @Composable () -> Unit, end: @Composable () -> Unit = { Box(Modifier.width(44.dp)) }) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        start()
        CoveText(label, style = CoveType.Meta, color = Cove.colors.muted, maxLines = 1)
        end()
    }
}

/** 44dp white circle with an X (the frames' close button); the tap target is 48dp. */
@Composable
fun CloseCircle(onClick: () -> Unit, label: String = "Close") {
    Box(
        Modifier.size(48.dp).pressable(onClick, role = Role.Button).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).background(Cove.colors.card, CoveShapes.Circle), contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.Close, Cove.colors.ink, size = 18.dp)
        }
    }
}

/** Bare chevron back button. */
@Composable
fun BackChevron(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).pressable(onClick, role = Role.Button).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) { CoveIcon(CoveIcons.ChevronLeft, Cove.colors.muted, size = 22.dp) }
}

/** Plain text action at the top right ("Finish"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, strong: Boolean = true) {
    Box(
        Modifier.heightIn(min = 48.dp).pressable(onClick, role = Role.Button).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = TrainingType.Row.copy(fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal), color = if (strong) Cove.colors.ink else Cove.colors.muted)
    }
}

/** Full-width dark pill, 56dp, dimmed and inert while [enabled] is false. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PillButton(
        text, { if (enabled) onClick() }, modifier.graphicsLayerAlpha(if (enabled) 1f else 0.35f).semantics { if (!enabled) contentDescription = "$text, not available" },
        height = 56.dp, textStyle = TrainingType.Button,
    )
}

/** White pill next to a primary button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        text, onClick, modifier, kind = ButtonKind.Secondary, height = 56.dp, horizontalPadding = 22.dp, container = Cove.colors.card,
        textStyle = TrainingType.Button.copy(fontWeight = FontWeight.Normal),
    )
}

/** Row of 56dp with a hairline above (except the first), label left and value right. */
@Composable
fun LabelRow(label: String, value: String, first: Boolean, muted: Color = Cove.colors.muted, valueColor: Color = Cove.colors.ink, onClick: (() -> Unit)? = null, chevron: Boolean = false) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Cove.colors.well))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (onClick != null) it.pressable(onClick, role = Role.Button) else it }.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(label, Modifier.weight(1f, fill = false), style = TrainingType.Row, color = muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            CoveText(value, style = TrainingType.Row, color = valueColor, textAlign = TextAlign.End, maxLines = 2)
            if (chevron) CoveIcon(CoveIcons.ChevronRight, Cove.colors.tail, size = 14.dp)
        }
    }
}

/**
 * Round 56dp stepper button that acts on the first press, then repeats while held (slowly, then faster).
 * Screen readers get a plain click action named by [label].
 */
@Composable
fun RepeatButton(glyph: String, label: String, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(56.dp)
            .background(Cove.colors.card, CoveShapes.Circle)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    val repeat = scope.launch {
                        delay(450)
                        var wait = 140L
                        while (true) {
                            step()
                            delay(wait)
                            if (wait > 60) wait -= 10
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        repeat.cancel()
                    }
                }
            }
            .semantics { role = Role.Button; contentDescription = label; onClick(label) { onStep(); true } },
        contentAlignment = Alignment.Center,
    ) { CoveText(glyph, style = CoveType.Section.copy(fontSize = 26.sp, fontWeight = FontWeight.Normal)) }
}

/**
 * One stepper line of frame 42: − number (with unit under it) +. Tapping the number lets the user type it.
 */
@Composable
fun StepperLine(
    value: String,
    unit: String,
    what: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onType: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        RepeatButton("−", "Decrease $what", onMinus)
        Column(
            Modifier.pressable(onType, onClickLabel = "Type the $what", role = Role.Button).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = "$what $value $unit" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CoveText(value, style = TrainingType.Big)
            CoveText(unit, style = TrainingType.Sub, color = Cove.colors.muted)
        }
        RepeatButton("+", "Increase $what", onPlus)
    }
}

/** Style of the large numbers of the type-in sheet. */
val TypeInStyle: TextStyle = CoveType.Figure.copy(fontSize = 44.sp, lineHeight = 52.sp)
