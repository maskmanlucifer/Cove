package app.cove.companion.feature.alarms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.cove.companion.BuildConfig
import app.cove.companion.container
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.design.CoveTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-screen ring UI shown over the lock screen, always in the dark theme so a 6 am alarm is
 * not blinding. Mirrors [AlarmRingService.ringing] and closes itself when the alarm stops.
 * Debug builds can open it with `--ez preview true` to inspect the layout.
 */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val preview = BuildConfig.DEBUG && intent.getBooleanExtra("preview", false)
        val previewState = RingState("preview", "Wake up", intent.getIntExtra("minutes", 6 * 60 + 30), 9)
        setContent {
            val live by AlarmRingService.ringing.collectAsState()
            val ring = if (preview) previewState else live
            LaunchedEffect(ring == null) { if (ring == null) finish() }
            ring ?: return@setContent
            val message by produceState("", ring.minutes) {
                val c = container
                // The database may be unreadable: ring with a plain message instead of failing.
                val first = runCatching {
                    c.plan.eventsOn(c.clock.now().toLocalDate()).first()
                        .map { it.startAt.toLocalDateTime() }
                        .map { it.hour * 60 + it.minute }
                        .filter { it >= ring.minutes }
                        .minOrNull()
                }.getOrNull()
                value = ringMessage(ring.minutes / 60, first)
            }
            val briefOn by produceState(false) { value = runCatching { container.settings.settings.first().briefOn }.getOrDefault(false) }
            CoveTheme(dark = true) {
                RingScreen(
                    ring = ring,
                    message = message,
                    onStop = { if (preview) finish() else AlarmRingService.stop(this) },
                    onSnooze = { if (preview) finish() else AlarmRingService.snooze(this) },
                    onPlayBrief = if (briefOn && !preview) ({
                        AlarmRingService.stop(this)
                        container.appScope.launch { container.briefPlayer.playToday() }
                    }) else null,
                )
            }
        }
    }
}
