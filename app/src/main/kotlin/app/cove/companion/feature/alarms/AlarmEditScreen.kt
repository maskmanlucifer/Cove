package app.cove.companion.feature.alarms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.plan.OptionList
import app.cove.companion.feature.plan.SheetRow
import app.cove.companion.feature.plan.SubPageHeader
import app.cove.companion.feature.plan.TitleField
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Page { Main, Label, Sound }

private val sounds = listOf("Soft rise", "Chime", "Ringtone").map { it to it }
private val snoozeOptions = listOf(5, 9, 10, 15)

/** Edit alarm sheet over the dimmed alarms list: time drum, days, label, sound, gentle wake, save and delete. */
@Composable
fun AlarmEditScreen(id: String, nav: Nav) {
    val vm = appViewModel(key = "alarm-$id") { AlarmEditViewModel(it, id) }
    val draft by vm.draft.collectAsState()
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.Main) }
    LaunchedEffect(Unit) { shown = true }
    val close: () -> Unit = {
        if (shown) {
            shown = false
            scope.launch {
                delay(220)
                nav.back()
            }
        }
    }
    BackHandler { if (page == Page.Main) close() else page = Page.Main }

    CoveScreen {
        CoveText(
            "Alarms",
            Modifier.coveTopInset().padding(start = 24.dp, top = 76.dp),
            style = CoveType.Title,
            color = Cove.colors.tail,
        )
        // The design sits the sheet 8dp from the physical bottom edge, over the gesture bar.
        Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars).imePadding()) {
            CoveSheet(shown && draft != null, close) {
                val alarm = draft ?: return@CoveSheet
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
                    when (page) {
                        Page.Main -> MainPage(alarm, vm, close, { page = it })
                        Page.Label -> LabelPage(alarm, vm) { page = Page.Main }
                        Page.Sound -> SoundPage(alarm, vm) { page = Page.Main }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPage(alarm: AlarmEntity, vm: AlarmEditViewModel, close: () -> Unit, open: (Page) -> Unit) {
    val scope = rememberCoroutineScope()
    DrumPicker(alarm.minutes, { m -> vm.edit { it.copy(minutes = m) } })
    DaysRow(alarm.daysMask) { i -> vm.edit { it.copy(daysMask = AlarmDays.flip(it.daysMask, i)) } }
    Column {
        SheetRow("Label", alarm.label.ifBlank { "None" }, { open(Page.Label) })
        SheetRow("Sound", alarm.sound, { open(Page.Sound) })
        GentleRow(alarm.gentleRise) { on -> vm.edit { it.copy(gentleRise = on) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Save", Modifier.weight(1f), primary = true) { scope.launch { vm.save(); close() } }
        if (vm.existing) ActionButton("Delete", Modifier.width(104.dp), primary = false) { scope.launch { vm.delete(); close() } }
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        modifier.height(56.dp).background(if (primary) c.ink else androidx.compose.ui.graphics.Color.Transparent, CoveShapes.Pill).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(
            text,
            style = CoveType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = if (primary) c.onInk else c.alert,
        )
    }
}

@Composable
private fun DaysRow(mask: Int, onToggle: (Int) -> Unit) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        "MTWTFSS".forEachIndexed { i, letter ->
            val on = (mask shr i) and 1 == 1
            Box(
                Modifier.size(40.dp).background(if (on) c.ink else c.canvas, CoveShapes.Circle).pressable({ onToggle(i) }),
                contentAlignment = Alignment.Center,
            ) {
                CoveText(
                    letter.toString(),
                    style = CoveType.Meta.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.onInk else c.muted,
                )
            }
        }
    }
}

@Composable
private fun GentleRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cove.colors
    Hairline()
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText("Gentle wake", style = CoveType.Body.copy(fontSize = 16.sp), color = c.muted)
            CoveText("Volume rises over 2 min", style = CoveType.Body.copy(fontSize = 13.sp, lineHeight = 17.55.sp), color = c.tail)
        }
        CoveSwitch(on, onChange)
    }
}

@Composable
private fun LabelPage(alarm: AlarmEntity, vm: AlarmEditViewModel, back: () -> Unit) {
    SubPageHeader("Label", back)
    TitleField(alarm.label, { v -> vm.edit { it.copy(label = v) } }, "Label", back, autofocus = true)
    PillButton("Done", back, height = 48.dp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SoundPage(alarm: AlarmEntity, vm: AlarmEditViewModel, back: () -> Unit) {
    SubPageHeader("Sound", back)
    OptionList(sounds, alarm.sound) { s -> vm.edit { it.copy(sound = s) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoveText("Snooze", style = CoveType.Meta, color = Cove.colors.muted)
        Segmented(
            snoozeOptions.map { "$it min" },
            snoozeOptions.indexOf(alarm.snoozeMinutes).coerceAtLeast(0),
            { i -> vm.edit { it.copy(snoozeMinutes = snoozeOptions[i]) } },
            fillWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PillButton("Done", back, height = 48.dp, modifier = Modifier.fillMaxWidth())
}
