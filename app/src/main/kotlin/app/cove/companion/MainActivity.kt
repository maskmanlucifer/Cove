package app.cove.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cove.companion.design.CoveTheme
import app.cove.companion.navigation.CoveNavHost
import app.cove.companion.navigation.DebugLaunch
import app.cove.companion.navigation.Routes
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.DebugSeed
import app.cove.companion.feature.alarms.DebugAlarms
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Single activity hosting the Compose navigation graph. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) handleDebugIntent()
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: return@setContent
            val dark = when (s.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CoveTheme(dark) {
                CoveNavHost(start = DebugLaunch.route ?: if (s.onboarded) Routes.Main else Routes.Welcome)
            }
        }
    }

    /**
     * Debug only. `--es now HH:mm` freezes the clock; `--ez seed true [--ez dark true] [--ez evening true]`
     * loads the design's sample data (`--es plan todos` swaps in the to-do frames' lists).
     * `--es route alarms` starts on that route; see also `DebugAlarms`.
     * `--es tab plan --es segment 1 --es sheet categories --es title Dentist` open a Plan tab view directly.
     */
    private fun handleDebugIntent() {
        intent.getStringExtra("now")?.let { hm ->
            val (h, m) = hm.split(":").map(String::toInt)
            Clock.frozenAt = LocalDateTime.of(LocalDate.now(), LocalTime.of(h, m)).toEpochMillis()
        }
        DebugLaunch.tab = intent.getStringExtra("tab")
        DebugLaunch.segment = intent.getStringExtra("segment")?.toIntOrNull()
        DebugLaunch.sheet = intent.getStringExtra("sheet")
        DebugLaunch.title = intent.getStringExtra("title")
        DebugLaunch.route = intent.getStringExtra("route")
        if (intent.getBooleanExtra("seed", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                DebugSeed.load(container, intent.getBooleanExtra("dark", false), intent.getBooleanExtra("evening", false), intent.getStringExtra("plan"))
            }
        }
        CoroutineScope(Dispatchers.IO).launch { DebugAlarms.handle(this@MainActivity, container, intent) }
    }
}
