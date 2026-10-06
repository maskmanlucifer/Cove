package app.cove.companion.feature.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.clockText
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveDock
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.Tab
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

private val TimeStyle = TextStyle(
    fontFamily = CoveType.Body.fontFamily,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1.4).sp,
    lineHeightStyle = CoveType.Body.lineHeightStyle,
)

/** Alarms list with on/off switches, the bedtime card and an add button. */
@Composable
fun AlarmsScreen(nav: Nav) {
    val vm = appViewModel { AlarmsViewModel(it) }
    val state by vm.state.collectAsState()
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size(44.dp).pressable(nav.back, role = Role.Button).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                    CoveIcon(CoveIcons.ChevronLeft, c.muted, size = 22.dp)
                }
                CoveText(state.nextText.orEmpty(), style = CoveType.Meta, color = c.muted)
                Box(
                    Modifier.size(44.dp).background(c.card, CoveShapes.Circle).pressable({ nav.go(Routes.alarmEdit()) }, role = Role.Button)
                        .semantics { contentDescription = "Add alarm" },
                    contentAlignment = Alignment.Center,
                ) { CoveIcon(CoveIcons.Plus, c.ink, size = 18.dp) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = DockClearance),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CoveText("Alarms", style = CoveType.Title, modifier = Modifier.semantics { heading() })
                AlarmList(state.alarms, vm::setEnabled) { nav.go(Routes.alarmEdit(it)) }
                Bedtime(state.bedtime) { nav.go(Routes.alarmEdit(it)) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            CoveDock(Tab.Me, onSelect = { nav.back() }, onVoice = { nav.go(Routes.Voice) })
        }
    }
}

@Composable
private fun AlarmList(alarms: List<AlarmEntity>, onToggle: (AlarmEntity, Boolean) -> Unit, onOpen: (String) -> Unit) {
    val c = Cove.colors
    if (alarms.isEmpty()) {
        CoveCard(padding = 20) {
            CoveText("No alarms yet. Tap + to add one.", style = CoveType.Body, color = c.muted)
        }
        return
    }
    Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
        alarms.forEachIndexed { i, alarm ->
            if (i > 0) Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).pressable({ onOpen(alarm.id) }, onClickLabel = "Edit alarm", role = Role.Button)
                    .semantics(mergeDescendants = true) {}.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // CSS grows this line box by ~7dp because the small suffix has its own 44dp line box.
                    TimeLabel(alarm.minutes, TimeStyle, suffixSize = 20.sp, modifier = Modifier.padding(bottom = 7.dp))
                    CoveText(alarmSubtitle(alarm.label, alarm.daysMask), style = CoveType.Meta, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                CoveSwitch(alarm.enabled, { onToggle(alarm, it) }, label = "Alarm ${clockText(alarm.minutes).let { it.digits + " " + it.suffix.trim() }}")
            }
        }
    }
}

@Composable
private fun Bedtime(bedtime: AlarmEntity?, onOpen: (String) -> Unit) {
    val c = Cove.colors
    val open = { onOpen(bedtime?.id ?: "bedtime") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoveText("Bedtime", Modifier.padding(start = 4.dp), style = CoveType.Meta, color = c.muted)
        Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp)) {
            BedtimeRow("Wind down", bedtime?.let { clockText(it.minutes).let { t -> t.digits + t.suffix } } ?: "Off", open)
            Hairline()
            BedtimeRow("Screen dims", "30 min before", open)
        }
    }
}

@Composable
private fun BedtimeRow(label: String, value: String, onClick: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable(onClick, role = Role.Button).semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(label, style = CoveType.Body.copy(fontSize = 16.sp), color = c.muted)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            CoveText(value, style = CoveType.Body.copy(fontSize = 16.sp))
            CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
        }
    }
}
