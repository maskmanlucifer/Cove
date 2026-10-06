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
import app.cove.companion.feature.plan.TITLE_MAX
import app.cove.companion.feature.plan.OptionList
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
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
import app.cove.companion.core.OneShot
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
    var pickingShow by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val blank = vm.name.text.isBlank()
    val saveGuard = remember { OneShot() }
    val submit: () -> Unit = { saveGuard.launch(scope) { if (vm.save()) { nav.back(); true } else false } }
    LaunchedEffect(Unit) { if (s.isNew) focus.requestFocus() }

    CoveScreen {
        CoveText("Habits", Modifier.coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), style = CoveType.Title, color = c.tail)
        Box(
            Modifier.fillMaxSize().background(if (c.isDark) c.scrim else c.scrim.copy(alpha = 0.18f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = nav.back),
        )
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, CoveShapes.SheetFloating, ambientColor = c.shadow, spotColor = c.shadow)
                    .background(c.card, CoveShapes.SheetFloating)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
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
                        inputTransformation = InputTransformation {
                            if (asCharSequence().contains('\n')) revertAllChanges()
                            else if (length > TITLE_MAX) replace(TITLE_MAX, length, "")
                        },
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
                    Segmented(listOf("Every day", "Some days"), s.cadence, vm::setCadence, Modifier.fillMaxWidth(), height = 40.dp, fillWidth = true)
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
                    SheetRow("Show on Today", s.show.label, onClick = { pickingShow = !pickingShow })
                    if (pickingShow) OptionList(ShowMode.entries.map { it to it.label }, s.show) { vm.setShow(it); pickingShow = false }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "Save", submit,
                        Modifier.weight(1f).graphicsLayerAlpha(if (blank) 0.4f else 1f),
                        height = 56.dp, textStyle = ButtonText,
                    )
                    PillButton("Cancel", nav.back, Modifier.widthIn(min = 96.dp), kind = ButtonKind.Text, height = 56.dp, textStyle = ButtonText.copy(fontWeight = FontWeight.Normal))
                }
                if (!s.isNew && !confirmDelete) {
                    PillButton(
                        "Delete habit", { confirmDelete = true },
                        Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Destructive,
                    )
                }
                if (!s.isNew && confirmDelete) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoveText("Delete this habit and its history?", style = CoveType.Meta, color = c.muted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Keep it", { confirmDelete = false }, Modifier.weight(1f), height = 48.dp, textStyle = ButtonText)
                            PillButton(
                                "Delete", { saveGuard.launch(scope) { vm.delete(); nav.back(); true } }, Modifier.widthIn(min = 104.dp),
                                kind = ButtonKind.Destructive, height = 48.dp, textStyle = ButtonText,
                            )
                        }
                    }
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
                    .pressable({ onToggle(maskBit(day)) }, role = Role.Checkbox)
                    .semantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        stateDescription = if (on) "On" else "Off"
                    },
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
