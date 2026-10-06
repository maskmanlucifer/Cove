package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/** Top-level destinations shown in the dock. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("Today", CoveIcons.Today, CoveIcons.TodayBold),
    Plan("Plan", CoveIcons.Plan, CoveIcons.PlanBold),
    Money("Money", CoveIcons.Money, CoveIcons.MoneyBold),
    Journal("Journal", CoveIcons.Journal, CoveIcons.JournalBold),
    Me("Me", CoveIcons.Me, CoveIcons.MeBold),
}

/** Floating pill dock with five tabs and the voice orb. */
@Composable
fun CoveDock(selected: Tab, onSelect: (Tab) -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val shadow = if (c.isDark) Color(0x4D000000) else Color(0x0F141420)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(24.dp, CoveShapes.Pill, ambientColor = shadow, spotColor = shadow)
            .background(c.card, CoveShapes.Pill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            val tint = if (on) c.ink else c.dockInactive
            Column(
                Modifier.weight(1f).height(60.dp).pressable({ onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                CoveIcon(if (on) tab.selectedIcon else tab.icon, tint, size = 22.dp)
                CoveText(
                    tab.label,
                    style = if (on) CoveType.Label else CoveType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = tint,
                )
            }
        }
        Box(Modifier.padding(start = 4.dp)) { VoiceOrb(onClick = onVoice) }
    }
}
