package app.cove.companion

import androidx.compose.runtime.getValue
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import app.cove.companion.design.CoveTheme
import app.cove.companion.design.resolveReduceMotion
import app.cove.companion.navigation.CoveNavHost
import app.cove.companion.navigation.DebugLaunch
import app.cove.companion.navigation.Routes
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.DebugSeed
import app.cove.companion.feature.voice.VoiceDebug
import app.cove.companion.feature.alarms.DebugAlarms
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Single activity hosting the Compose navigation graph. */
class MainActivity : ComponentActivity() {
    private var debugRoute: String? = null

    /** Bumped whenever something asks to open straight into listening (tile, shortcut, debug). */
    private val voiceRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) handleDebugIntent()
        if (intent.action == ACTION_LISTEN) voiceRequest.intValue++
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: return@setContent
            val dark = when (s.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val animationsOff = remember { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            CoveTheme(dark, textScale = s.textScale, reduceMotion = resolveReduceMotion(s.reduceMotion, animationsOff)) {
                CoveNavHost(
                    start = debugRoute ?: DebugLaunch.route ?: if (s.onboarded) Routes.Main else Routes.Welcome,
                    voiceRequest = voiceRequest.intValue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) handleDebugIntent()
        if (intent.action == ACTION_LISTEN) voiceRequest.intValue++
    }

    /**
     * Debug only. `--es now HH:mm` freezes the clock (`--es date yyyy-MM-dd` picks the day); `--es route <route>` starts on that route.
     * `--ez seed true [--ez dark true] [--ez evening true] [--ez moneyLogged true] [--es plan todos|empty|drag]` loads the design's sample data.
     * `--es route alarms` starts on that route; see also `DebugAlarms`.
     * `--es tab plan --es segment 1 --es sheet categories --es title Dentist` open a Plan tab view directly.
     * `--es voiceState listening|result|partial|saved|micoff --es transcript "..."` opens the Voice screen in that state.
     */
    private fun handleDebugIntent() {
        debugRoute = intent.getStringExtra("route")
        intent.getStringExtra("now")?.let { hm ->
            val (h, m) = hm.split(":").map(String::toInt)
            Clock.frozenAt = LocalDateTime.of(intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now(), LocalTime.of(h, m)).toEpochMillis()
        }
        DebugLaunch.tab = intent.getStringExtra("tab")
        DebugLaunch.segment = intent.getStringExtra("segment")?.toIntOrNull()
        DebugLaunch.sheet = intent.getStringExtra("sheet")
        DebugLaunch.title = intent.getStringExtra("title")
        val voiceState = intent.getStringExtra("voiceState")
        VoiceDebug.set(voiceState, intent.getStringExtra("transcript"), intent.getIntExtra("voiceSeconds", 7))
        if (voiceState != null && voiceState != "saved") voiceRequest.intValue++
        val seed = intent.getBooleanExtra("seed", false)
        if (seed || voiceState == "saved") {
            CoroutineScope(Dispatchers.IO).launch {
                if (seed) {
                    DebugSeed.load(
                        container, intent.getBooleanExtra("dark", false), intent.getBooleanExtra("evening", false),
                        intent.getStringExtra("plan"), intent.getBooleanExtra("moneyLogged", false),
                    )
                }
                VoiceDebug.runSaved(container)
            }
        }
        CoroutineScope(Dispatchers.IO).launch { DebugAlarms.handle(this@MainActivity, container, intent) }
    }

    companion object {
        /** Opens the app straight into listening; sent by the Quick Settings tile and the shortcut. */
        const val ACTION_LISTEN = "app.cove.companion.action.LISTEN"
    }
}
