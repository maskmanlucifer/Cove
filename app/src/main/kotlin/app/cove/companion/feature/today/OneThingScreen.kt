package app.cove.companion.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav

/** One-thing mode as its own route ([app.cove.companion.navigation.Routes.OneThing]). */
@Composable
fun OneThingScreen(nav: Nav) {
    CoveScreen { OneThingContent(nav) }
}

/** The calm single-task view; shown by [OneThingScreen] and inside Today when the mode is on. */
@Composable
fun OneThingContent(nav: Nav) {
    val vm = appViewModel { OneThingViewModel(it) }
    val state by vm.state.collectAsState()
    val c = Cove.colors
    val buttonText = CoveType.Body.copy(fontWeight = FontWeight.Medium)
    Column(Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CoveText("One-thing mode", style = CoveType.Meta, color = c.muted)
            Box(Modifier.height(44.dp).pressable({ vm.exit { nav.home() } }), contentAlignment = Alignment.Center) {
                CoveText("Exit", style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.muted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            if (state.loaded) {
                CoveText("Right now", style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.muted)
                CoveText(
                    state.title ?: "Nothing waiting",
                    style = CoveType.Hero.copy(fontWeight = FontWeight.Medium, lineHeight = 48.sp, lineBreak = LineBreak.Paragraph),
                )
                CoveText(
                    if (state.title == null) "You are all caught up." else "That’s all. The rest can wait.",
                    style = CoveType.Body, color = c.muted,
                )
            }
        }
        if (state.title != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Done", vm::done, Modifier.weight(1f), height = 64.dp, textStyle = buttonText)
                PillButton(
                    "Not now", vm::notNow, Modifier.width(120.dp), kind = ButtonKind.Secondary, height = 64.dp,
                    container = c.card, textStyle = CoveType.Body,
                )
            }
        }
    }
}
