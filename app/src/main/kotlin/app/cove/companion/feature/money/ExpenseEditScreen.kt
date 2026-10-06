package app.cove.companion.feature.money

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appSavedViewModel
import app.cove.companion.core.rupees
import app.cove.companion.core.toEpochMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Add or edit an expense: Spent/Received, amount on a custom keypad, category, note, time and payment method. */
@Composable
fun ExpenseEditScreen(id: String, nav: Nav) {
    val vm = appSavedViewModel(key = "expense-$id") { c, saved -> ExpenseEditViewModel(c, id, saved) }
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var noteFocused by remember { mutableStateOf(false) }
    var methodSheet by remember { mutableStateOf(false) }
    val c = Cove.colors
    val today = remember { s.whenMillis.toLocalDate() }

    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton(CoveIcons.Close, nav.back)
                KindToggle(listOf("Spent", "Received"), if (s.received) 1 else 0, { vm.setReceived(it == 1) }, Modifier.align(Alignment.Top))
                Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                    if (!s.isNew) {
                        CoveText(
                            "Delete", Modifier.pressable({ vm.delete(); nav.back() }).padding(vertical = 12.dp),
                            style = CoveType.Meta, color = c.alert,
                        )
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AmountBlock(s, vm::setNote, focus = { noteFocused = it }, onDone = { focus.clearFocus() })
                CategoryChips(s, vm::setCategory, onNew = { nav.go(Routes.moneyCategory()) })
                RowsCard(Modifier.padding(horizontal = 24.dp), vertical = 0.dp) {
                    PickRow("When", MoneyMath.whenText(s.whenMillis, today), { pickDateTime(context, s.whenMillis, vm::setWhen) }, divider = false)
                    PickRow("Paid with", s.paidWith, { methodSheet = true })
                }
                Spacer(Modifier.weight(1f))
                val label = if (s.paise > 0) "Save " + rupees(s.paise) else "Save"
                PillButton(
                    label,
                    onClick = {
                        if (s.paise > 0) scope.launch {
                            vm.save()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            nav.back()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().graphicsLayerAlpha(if (s.paise > 0) 1f else 0.35f),
                    height = 56.dp,
                    textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium),
                )
                AnimatedVisibility(!noteFocused) {
                    AmountKeypad(vm::key, vm::back, vm::clear, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        CoveSheet(methodSheet, onDismiss = { methodSheet = false }) {
            MethodSheet(s.paidWith) { vm.setPaidWith(it); methodSheet = false }
        }
    }
}

@Composable
private fun AmountBlock(s: ExpenseEditState, onNote: (String) -> Unit, focus: (Boolean) -> Unit, onDone: () -> Unit) {
    val c = Cove.colors
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoveText(AmountInput.display(s.amount), style = MoneyType.Big, color = if (s.amount.isEmpty()) c.tail else c.ink)
            Box(Modifier.padding(start = 3.dp).width(2.dp).height(52.dp).background(c.ink))
        }
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val requester = remember { FocusRequester() }
        val textWidth = with(density) { measurer.measure(s.note, CoveType.Body).size.width.toDp() }
        Row(Modifier.pressable({ requester.requestFocus() }), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                s.note, onNote,
                Modifier.width(textWidth + 2.dp).focusRequester(requester).onFocusChanged { focused = it.isFocused; focus(it.isFocused) },
                singleLine = true,
                textStyle = CoveType.Body.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
            )
            if (s.note.isEmpty()) CoveText("Add a note", style = CoveType.Body, color = c.placeholder)
            else if (!focused) CoveText(" · add a note", style = CoveType.Body, color = c.placeholder)
        }
    }
}

@Composable
private fun CategoryChips(s: ExpenseEditState, onPick: (String) -> Unit, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.categories.forEach { cat ->
            val on = cat.id == s.categoryId
            Chip(cat.name, on) { onPick(cat.id) }
        }
        Chip("+ New", false, onNew)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        Modifier
            .height(40.dp)
            .background(if (selected) c.ink else c.card, CoveShapes.Pill)
            .pressable(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(
            text,
            style = CoveType.Button.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = if (selected) c.onInk else c.ink,
        )
    }
}

@Composable
private fun MethodSheet(selected: String, onPick: (String) -> Unit) {
    val c = Cove.colors
    Column {
        CoveText("Paid with", Modifier.padding(vertical = 12.dp), style = CoveType.Meta, color = c.muted)
        PaymentMethods.forEachIndexed { i, method ->
            if (i > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ onPick(method) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText(method, style = CoveType.Body)
                if (method == selected) CoveIcon(CoveIcons.Check, c.ink, size = 18.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Date then time, with the system dialogs; keeps the minute-level time of [current]. */
private fun pickDateTime(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val now = current.toLocalDateTime()
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            onPicked(LocalDateTime.of(y, m + 1, d, h, min).toEpochMillis())
        }, now.hour, now.minute, false).show()
    }, now.year, now.monthValue - 1, now.dayOfMonth).show()
}
