package app.cove.companion.feature.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset

/**
 * Always-visible way to silence a ringing alarm from inside the app, for when notifications are off and Android
 * did not raise the full-screen ring screen. Draws nothing while no alarm rings.
 */
@Composable
fun RingingBanner(modifier: Modifier = Modifier) {
    val ring by AlarmRingService.ringing.collectAsState()
    val current = ring ?: return
    val context = LocalContext.current
    val time = clockText(current.minutes)
    Column(
        modifier.coveTopInset().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .background(Cove.colors.card, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(current.label.ifBlank { "Alarm" }, style = CoveType.BodyMedium)
        CoveText("Ringing since ${time.digits}${time.suffix}", style = CoveType.Meta, color = Cove.colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Stop", { AlarmRingService.stop(context) }, height = 44.dp)
            PillButton("Snooze ${current.snoozeMinutes} min", { AlarmRingService.snooze(context) }, kind = ButtonKind.Secondary, height = 44.dp)
        }
    }
}
