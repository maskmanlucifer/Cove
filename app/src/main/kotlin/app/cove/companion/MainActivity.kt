package app.cove.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import app.cove.companion.design.CoveTheme
import app.cove.companion.navigation.CoveNavHost
import app.cove.companion.navigation.Routes
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.DebugSeed
import app.cove.companion.feature.voice.VoiceDebug
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Single activity hosting the Compose navigation graph. */
class MainActivity : ComponentActivity() {
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
            CoveTheme(dark) {
                CoveNavHost(start = if (s.onboarded) Routes.Main else Routes.Welcome, voiceRequest = voiceRequest.intValue)
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
     * Debug only. `--es now HH:mm` freezes the clock; `--ez seed true [--ez dark true] [--ez evening true]`
     * loads the design's sample data; `--es voiceState listening|result|partial|saved|micoff --es transcript "..."`
     * opens the Voice screen in that state.
     */
    private fun handleDebugIntent() {
        intent.getStringExtra("now")?.let { hm ->
            val (h, m) = hm.split(":").map(String::toInt)
            Clock.frozenAt = LocalDateTime.of(LocalDate.now(), LocalTime.of(h, m)).toEpochMillis()
        }
        val voiceState = intent.getStringExtra("voiceState")
        VoiceDebug.set(voiceState, intent.getStringExtra("transcript"), intent.getIntExtra("voiceSeconds", 7))
        if (voiceState != null && voiceState != "saved") voiceRequest.intValue++
        val seed = intent.getBooleanExtra("seed", false)
        if (seed || voiceState == "saved") {
            CoroutineScope(Dispatchers.IO).launch {
                if (seed) DebugSeed.load(container, intent.getBooleanExtra("dark", false), intent.getBooleanExtra("evening", false))
                VoiceDebug.runSaved(container)
            }
        }
    }

    companion object {
        /** Opens the app straight into listening; sent by the Quick Settings tile and the shortcut. */
        const val ACTION_LISTEN = "app.cove.companion.action.LISTEN"
    }
}
