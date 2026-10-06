package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DialogSystemBars
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.ValueRow
import app.cove.companion.design.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Style of sheet titles and the editable to-do title. */
val SheetTitleStyle = CoveType.Section.copy(lineHeight = 32.sp)

/**
 * Hosts a [CoveSheet] in its own window so it covers the dock. [content] receives `close`,
 * which plays the exit animation before calling [onDismiss].
 */
@Composable
fun PlanSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Int = 24,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        DialogSystemBars()
        var shown by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { shown = true }
        val close: () -> Unit = {
            if (shown) {
                shown = false
                scope.launch {
                    delay(220)
                    onDismiss()
                }
            }
        }
        Box(Modifier.fillMaxSize().imePadding()) {
            CoveSheet(shown, close, modifier.statusBarsPadding()) {
                Column(
                    Modifier.fillMaxWidth().let { if (fillHeight) it.fillMaxHeight() else it.verticalScroll(rememberScrollState()) },
                    verticalArrangement = Arrangement.spacedBy(gap.dp),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
                    content(close)
                }
            }
        }
    }
}

/** A sheet row: hairline above, muted label, value and a small chevron. */
@Composable
fun SheetRow(label: String, value: String, onClick: () -> Unit, valueTail: String? = null, placeholder: Boolean = false) {
    val c = Cove.colors
    Hairline()
    ValueRow(
        label,
        onClick = onClick,
        trailing = {
            val style = CoveType.Body.copy(fontSize = TextUnit(16f, TextUnitType.Sp))
            if (placeholder) CoveText(value, style = style, color = c.placeholder) else CoveText(value, valueTail ?: "", style = style)
            CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
        },
    )
}

/** Sheet row whose trailing content is a control such as a switch. */
@Composable
fun SheetControlRow(label: String, control: @Composable () -> Unit) {
    Hairline()
    ValueRow(label, trailing = control)
}

/** Longest title any sheet accepts; longer input is cut. */
const val TITLE_MAX = 120

/** Characters left before the title counter shows. */
private const val TITLE_COUNTER_FROM = 100

/**
 * Title-sized text field that behaves as one logical line: the Done key (or a pasted line break) ends
 * editing instead of inserting a newline, input is cut at [TITLE_MAX] and a counter appears near the limit.
 */
@Composable
fun TitleField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    autofocus: Boolean = false,
) {
    val c = Cove.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    if (autofocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val finish = {
        keyboard?.hide()
        focusManager.clearFocus()
        onDone()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty()) CoveText(placeholder, style = SheetTitleStyle, color = c.placeholder)
            BasicTextField(
                value,
                { raw ->
                    onChange(titleInput(raw))
                    if (raw.contains('\n')) finish()
                },
                Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = SheetTitleStyle.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish() }),
            )
        }
        if (value.length >= TITLE_COUNTER_FROM) {
            CoveText("${value.length} / $TITLE_MAX", style = CoveType.Meta, color = if (value.length >= TITLE_MAX) c.alert else c.muted)
        }
    }
}

/** Input rule for titles: line breaks removed, leading spaces dropped, capped at [TITLE_MAX]. */
fun titleInput(raw: String): String = raw.replace("\n", "").replace("\r", "").trimStart().take(TITLE_MAX)

/** Single-line field used for inline adds and renames; calls [onFocusLost] when it loses focus after having it. */
@Composable
fun InlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = CoveType.Body,
    onFocusLost: () -> Unit = {},
) {
    val c = Cove.colors
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) CoveText(placeholder, style = style, color = c.placeholder)
        BasicTextField(
            value, onChange,
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) onFocusLost()
                },
            textStyle = style.copy(color = c.ink),
            singleLine = true,
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}

/** Header of a sub-page inside a sheet: back chevron and a title. */
@Composable
fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onBack, onClickLabel = "Back", role = Role.Button).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronLeft, Cove.colors.ink, size = 22.dp)
        }
        CoveText(title, style = CoveType.Heading)
    }
}

/** A list of single-choice rows with a tick on the selected one. */
@Composable
fun <T> OptionList(options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ onPick(value) }, role = Role.RadioButton).semantics { this.selected = value == selected },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText(label, style = CoveType.Body, color = if (value == selected) Cove.colors.ink else Cove.colors.muted)
                if (value == selected) CoveIcon(CoveIcons.Check, Cove.colors.ink, size = 18.dp)
            }
        }
    }
}
