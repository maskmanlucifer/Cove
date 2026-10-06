package app.cove.companion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

private val FieldText = CoveType.Body.copy(fontSize = 18.sp, lineHeight = 24.3.sp)

/**
 * Frame 32: type the command. Used when the mic is off or unavailable, and for Type instead,
 * Type it, Edit and Change; submitting runs the same path as speech.
 */
@Composable
fun TypingView(s: VoiceState, onClose: () -> Unit, onChange: (String) -> Unit, onSubmit: () -> Unit, onTalk: () -> Unit, onSettings: () -> Unit) {
    val c = Cove.colors
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(s.typed, TextRange(s.typed.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CloseButton(onClose) }
        if (s.micOff) BalancedText("Voice is off.", " Typing works just as well.", CoveType.Title)
        else BalancedText("Type it.", " I’ll sort it out.", CoveType.Title)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.card, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (field.text.isEmpty()) CoveText("Remind me to call mum at 6", style = FieldText, color = c.placeholder)
            BasicTextField(
                field,
                { field = it; onChange(it.text) },
                Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = FieldText.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            )
        }
        Row {
            CoveText("Want to talk instead? ", style = CoveType.Meta, color = c.muted)
            Link(if (s.micOff) "Allow microphone in settings" else "Listen instead", if (s.micOff) onSettings else onTalk)
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    val line = Cove.colors.quiet
    Box(
        Modifier.pressable(onClick).drawBehind {
            val y = size.height - 1.dp.toPx()
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        },
    ) { CoveText(text, style = CoveType.Meta, color = Cove.colors.ink) }
}
