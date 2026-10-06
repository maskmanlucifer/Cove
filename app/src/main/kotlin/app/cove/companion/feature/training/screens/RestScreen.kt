package app.cove.companion.feature.training.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.FitText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.rest.RestAlarm
import app.cove.companion.feature.training.rest.RestStore
import app.cove.companion.feature.training.ui.CloseCircle
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.SecondaryButton
import app.cove.companion.feature.training.ui.TrainingTopBar
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.delay

/** Frame 43: rest countdown with +30 s and Ready. The end time is stored, so it keeps counting with the screen off. */
@Composable
fun RestScreen(nav: Nav) {
    val ctx = LocalContext.current
    val clock = ctx.container.clock
    val store = remember { RestStore(ctx) }
    var rest by remember { mutableStateOf(store.get()) }
    var now by remember { mutableLongStateOf(clock.now()) }
    val haptic = LocalHapticFeedback.current
    var buzzed by remember { mutableStateOf(false) }
    val c = Cove.colors

    LaunchedEffect(Unit) { while (true) { now = clock.now(); delay(250) } }
    val r = rest
    LaunchedEffect(Unit) { if (store.get() == null) { delay(150); nav.back() } }
    LaunchedEffect(r != null && r.isOver(now)) {
        if (r != null && r.isOver(now) && !buzzed) { buzzed = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    fun ready() {
        if (rest == null) return
        store.clear(); RestAlarm.cancel(ctx); rest = null
        nav.back()
    }

    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TrainingTopBar(r?.header.orEmpty(), { CloseCircle({ nav.back() }, "Close, the rest keeps counting") })
            Column(Modifier.weight(1f).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp)) {
                val secs = r?.remainingSeconds(now) ?: 0
                val over = r?.isOver(now) == true
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoveText(if (over) "Rest is over" else "Rest", style = TrainingType.Sub, color = c.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    FitText(TrainingText.clock(secs), Modifier.semantics { contentDescription = TrainingText.spokenClock(secs) }, style = TrainingType.Rest)
                    r?.nextLabel?.takeIf { it.isNotBlank() }?.let { CoveText("Next · $it", style = CoveType.Body, color = c.muted, textAlign = TextAlign.Center) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("+30 s", { r?.let { x -> val n = x.extended(30); store.set(n); RestAlarm.schedule(ctx, n); rest = n; buzzed = false } })
                    PrimaryButton("Ready", { ready() }, Modifier.weight(1f))
                }
            }
        }
    }
}
