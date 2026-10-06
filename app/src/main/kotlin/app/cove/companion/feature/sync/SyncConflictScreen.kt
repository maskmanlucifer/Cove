package app.cove.companion.feature.sync

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.data.sync.ConflictSide
import app.cove.companion.data.sync.Resolution
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav

/** "Which one should stay?": settles a change made on this phone and another device. */
@Composable
fun SyncConflictScreen(nav: Nav) {
    val vm = appViewModel { SyncConflictViewModel(it) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state) { if (state is ConflictState.None) nav.back() }
    CoveScreen {
        val open = state as? ConflictState.Open ?: return@CoveScreen
        val ui = open.ui
        var keepOther by remember(ui.entity.rowId) { mutableStateOf(false) }
        Illustration(Scene.Synced, Modifier.align(Alignment.TopCenter).padding(top = 120.dp).height(180.dp))
        CoveSheet(visible = true, onDismiss = nav.back) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BalancedText("Which one should stay?", "", CoveType.Section.copy(lineHeight = 32.sp))
                    CoveText(ui.summary.sentence, style = CoveType.Button.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp), color = Cove.colors.muted)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VersionCard(ui.summary.local, !keepOther) { keepOther = false }
                    VersionCard(ui.summary.remote, keepOther) { keepOther = true }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val chosen = if (keepOther) ui.summary.remote else ui.summary.local
                    ActionPill("Keep ${chosen.value}") { vm.resolve(ui.entity, if (keepOther) Resolution.KeepOther else Resolution.KeepThisPhone) }
                    Box(Modifier.fillMaxWidth().height(48.dp).pressable({ vm.resolve(ui.entity, Resolution.KeepBoth) }), contentAlignment = Alignment.Center) {
                        CoveText("Keep both", style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = Cove.colors.muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        Modifier.fillMaxWidth().height(56.dp).background(c.ink, CoveShapes.Pill).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { CoveText(text, style = CoveType.Button.copy(fontSize = 16.sp, lineHeight = 21.6.sp), color = c.onInk) }
}

@Composable
private fun VersionCard(side: ConflictSide, selected: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (selected) it.border(1.5.dp, c.ink, shape) else it.background(c.canvas, shape) }
            .pressable(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val ring = c.ring
        Box(Modifier.size(22.dp).border(if (selected) 7.dp else 1.5.dp, if (selected) c.accent else ring, CoveShapes.Circle))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText(side.value, style = CoveType.Body)
            CoveText(side.meta, style = CoveType.Meta, color = c.muted)
        }
    }
}
