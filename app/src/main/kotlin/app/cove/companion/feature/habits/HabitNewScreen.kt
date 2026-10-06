package app.cove.companion.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.plan.SheetRow
import app.cove.companion.feature.plan.TimePanel
import app.cove.companion.navigation.Nav
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val NameStyle = CoveType.Section.copy(lineHeight = 32.sp)
private val ButtonText = CoveType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium)

/** New habit (or edit, when [id] is a habit id) as a floating sheet over the dimmed Habits page. */
@Composable
fun HabitNewScreen(nav: Nav, id: String = "new") {
    val vm = appViewModel(key = "habit-edit-$id") { HabitEditViewModel(it, id) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val c = Cove.colors
    val focus = remember { FocusRequester() }
    var picking by rememberSaveable { mutableStateOf(false) }
    val blank = vm.name.text.isBlank()
    val submit: () -> Unit = { scope.launch { if (vm.save()) nav.back() } }
    LaunchedEffect(Unit) { if (s.isNew) focus.requestFocus() }

    CoveScreen {
        CoveText("Habits", Modifier.coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), style = CoveType.Title, color = c.tail)
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
                    CoveText(if (s.isNew) "New habit" else "Edit habit", style = CoveType.Meta, color = c.muted)
                    BasicTextField(
                        vm.name,
                        Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = NameStyle.copy(color = c.ink),
                        cursorBrush = SolidColor(c.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        onKeyboardAction = { submit() },
                        inputTransformation = InputTransformation { if (asCharSequence().contains('\n')) revertAllChanges() },
                        decorator = { inner ->
                            Box {
                                if (vm.name.text.isEmpty()) CoveText("Name", style = NameStyle, color = c.placeholder)
                                inner()
                            }
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoveText("How often", style = CoveType.Meta, color = c.muted)
                    Segmented(listOf("Every day", "Some days", "Weekly"), s.cadence, vm::setCadence, Modifier.fillMaxWidth(), height = 40.dp, fillWidth = true)
                    if (s.cadence != 0) DayCircles(s.daysMask, vm::toggleDay)
                }
                Column {
                    val remind = s.remindMinutes
                    SheetRow(
                        "Remind me",
                        remind?.let { clockText(it).digits } ?: "Off",
                        onClick = { picking = !picking },
                        valueTail = remind?.let { clockText(it).suffix.let { sfx -> sfx } },
                        placeholder = remind == null,
                    )
                    if (picking) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            TimePanel(remind ?: (7 * 60 + 30), vm::setReminder)
                            PillButton(
                                "No reminder", { vm.setReminder(null); picking = false },
                                kind = ButtonKind.Text,
                            )
                        }
                    }
                    SheetRow("Show on Today", s.show.label, onClick = vm::cycleShow)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (s.isNew) "Add habit" else "Save", submit,
                        Modifier.weight(1f).graphicsLayerAlpha(if (blank) 0.4f else 1f),
                        height = 56.dp, textStyle = ButtonText,
                    )
                    PillButton("Cancel", nav.back, Modifier.width(104.dp), kind = ButtonKind.Secondary, height = 56.dp, container = c.canvas, textStyle = ButtonText)
                }
                if (!s.isNew) {
                    PillButton(
                        "Delete habit", { scope.launch { vm.delete(); nav.back() } },
                        Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Destructive,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCircles(mask: Int, onToggle: (Int) -> Unit) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = mask and maskBit(day) != 0
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (on) c.ink else c.canvas, CoveShapes.Circle)
                    .pressable({ onToggle(maskBit(day)) }),
                contentAlignment = Alignment.Center,
            ) {
                CoveText(
                    day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = CoveType.Meta.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.onInk else c.muted,
                )
            }
        }
    }
}
