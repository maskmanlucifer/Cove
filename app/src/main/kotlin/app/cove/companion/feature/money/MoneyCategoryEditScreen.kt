package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.rupees
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.launch

private val NameStyle = CoveType.Section

/** New or edit category as a floating sheet: name, Spending/Income, budget, carry-over and the 80% alert. */
@Composable
fun MoneyCategoryEditScreen(id: String, nav: Nav) {
    val vm = appViewModel(key = "category-edit-$id") { CategoryEditViewModel(it, id) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val c = Cove.colors
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (s.isNew) nameFocus.requestFocus() }

    CoveScreen {
        CoveText("Categories", Modifier.coveTopInset().padding(horizontal = 24.dp, vertical = 76.dp), style = CoveType.Title, color = c.tail)
        Box(
            Modifier.fillMaxSize().background(if (c.isDark) c.scrim else c.scrim.copy(alpha = 0.18f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = nav.back),
        )
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, CoveShapes.SheetFloating, ambientColor = Color(0x1A141420), spotColor = Color(0x1A141420))
                    .background(c.card, CoveShapes.SheetFloating)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SheetHandle(Modifier.align(Alignment.CenterHorizontally))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoveText(if (s.isNew) "New category" else "Edit category", style = CoveType.Meta, color = c.muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            s.name, vm::setName,
                            Modifier.weight(1f, fill = false).focusRequester(nameFocus),
                            singleLine = true,
                            textStyle = NameStyle.copy(color = c.ink),
                            cursorBrush = SolidColor(c.ink),
                            decorationBox = { inner ->
                                Box {
                                    if (s.name.isEmpty()) CoveText("Name", style = NameStyle, color = c.placeholder)
                                    inner()
                                }
                            },
                        )
                    }
                }
                KindToggle(listOf("Spending", "Income"), if (s.income) 1 else 0, { vm.setIncome(it == 1) }, Modifier.fillMaxWidth(), fill = true)
                Column {
                    BudgetRow(s.budget, vm::setBudget)
                    ToggleRow("Carry over what’s left", s.carryOver, vm::setCarryOver)
                    ToggleRow("Tell me at 80%", s.alertAt80, vm::setAlert)
                }
                CoveText(hint(s.name), style = MoneyType.Note, color = c.muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (s.isNew) "Create" else "Save",
                        onClick = { scope.launch { if (vm.save()) nav.back() } },
                        modifier = Modifier.weight(1f),
                        height = 56.dp,
                        textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium),
                    )
                    PillButton(
                        "Cancel", nav.back, Modifier.width(104.dp), kind = ButtonKind.Secondary, height = 56.dp,
                        container = c.canvas, textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium),
                    )
                }
                if (!s.isNew) {
                    PillButton("Delete category", { vm.delete(nav.back) }, Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Destructive)
                }
            }
        }
    }
}

private fun hint(name: String): String {
    val word = name.trim().lowercase().let { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }
    return if (word.isEmpty()) "Give it a name, then say it when you log by voice and Cove files it here."
    else "Say “$word” when you log by voice and Cove files it here."
}

@Composable
private fun BudgetRow(budget: String, onChange: (String) -> Unit) {
    val c = Cove.colors
    val requester = remember { FocusRequester() }
    RowDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ requester.requestFocus() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CoveText("Monthly budget", style = MoneyType.Row, color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            val value = budget.toLongOrNull()?.let { rupees(it * 100) }
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val fieldWidth = with(density) { measurer.measure(value?.removePrefix("₹") ?: "", MoneyType.Row).size.width.toDp() } + 4.dp
            if (value == null) CoveText("None", style = MoneyType.Row, color = c.placeholder) else CoveText("₹", style = MoneyType.Row)
            BasicTextField(
                value?.removePrefix("₹") ?: "", { onChange(it) },
                Modifier.width(fieldWidth).focusRequester(requester),
                singleLine = true,
                textStyle = MoneyType.Row.copy(color = c.ink, fontFeatureSettings = "tnum"),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(6.dp))
            CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CoveText(label, style = MoneyType.Row, color = Cove.colors.muted)
        CoveSwitch(checked, onChange)
    }
}
