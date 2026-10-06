package app.cove.companion

import androidx.compose.runtime.getValue
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import app.cove.companion.core.net.ConnectivityMonitor
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.DebugSeed
import app.cove.companion.feature.brief.BriefDebug
import app.cove.companion.feature.suggest.SuggestDebug
import app.cove.companion.feature.voice.VoiceDebug
import app.cove.companion.feature.alarms.DebugAlarms
import app.cove.companion.feature.widgets.DebugWidgets
import app.cove.companion.feature.security.LockScreen
import app.cove.companion.security.DebugSecurity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Single activity hosting the Compose navigation graph. */
class MainActivity : FragmentActivity() {
    private var debugRoute: String? = null
    private var debugScreenshots = false

    /** Bumped whenever something asks to open straight into listening (tile, shortcut, debug). */
    private val voiceRequest = mutableIntStateOf(0)

    /** Last [voiceRequest] already acted on, so a request made while locked runs once after unlocking and never again. */
    private var handledVoice = 0

    /** Shows Google's Drive consent screen when the uploader needs it and hands the result back. */
    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        container.driveKit.onConsentResult(it.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.driveKit.consentRequests.collect { pending ->
                    if (pending != null) {
                        container.driveKit.consumeConsent()
                        driveConsent.launch(IntentSenderRequest.Builder(pending).build())
                    }
                }
            }
        }
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
            val locked by container.appLock.locked.collectAsState()
            val showLock = s.biometricLock && locked
            SideEffect { applySecureFlag(s.biometricLock && (locked || s.hideInRecents)) }
            CoveTheme(dark, textScale = s.textScale, reduceMotion = resolveReduceMotion(s.reduceMotion, animationsOff)) {
                if (showLock) {
                    LockScreen(
                        onUnlock = container.appLock::unlock,
                        onTurnOff = { lifecycleScope.launch { container.settings.update { it.copy(biometricLock = false) } } },
                    )
                } else {
                    val pending = voiceRequest.intValue
                    val request = if (pending > handledVoice) pending else 0
                    LaunchedEffect(request) { if (request > 0) handledVoice = request }
                    CoveNavHost(
                        start = debugRoute ?: DebugLaunch.route ?: if (s.onboarded) Routes.Main else Routes.Welcome,
                        voiceRequest = request,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        container.appLock.onBackground()
    }

    /** Hides the window from recents and screenshots while [secure]. */
    private fun applySecureFlag(secure: Boolean) {
        if (secure && !(BuildConfig.DEBUG && debugScreenshots)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
     * `--ez conflict true` (with seed) adds the sync conflict from frame 31; open it with `--es route sync/conflict`.
     * `--es tab plan --es segment 1 --es sheet categories --es title Dentist` open a Plan tab view directly.
     * `--ez fakeDrive true [--ez driveRun true]` uses a folder-backed fake Drive with a seeded pending photo; driveRun uploads it and backs up.
     * `--es suggest late-night` fakes a 1:40 am phone use so the late-night suggestion appears; `--es briefAt 51/124` freezes the brief player
     * at elapsed/total seconds; `--ez offline true` forces the offline look.
     * `--ez appLock true|false` switches the app lock setting without authenticating; `--ez lockNow true` locks immediately; `--ez screenshots true` drops FLAG_SECURE so adb screencap works;
     * `--ez plainDb true` rewrites the database as plaintext and kills the process, so the next launch runs the plaintext migration.
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
        intent.getStringExtra("suggest")?.let {
            val day = Clock.System.now().toLocalDate()
            SuggestDebug.lastUse = LocalDateTime.of(day, LocalTime.of(1, 40)).toEpochMillis()
        }
        BriefDebug.frozen = intent.getStringExtra("briefAt")?.split("/")?.let { it[0].toInt() to it[1].toInt() }
        ConnectivityMonitor.forceOffline = intent.getBooleanExtra("offline", false)
        if (intent.hasExtra("appLock")) {
            val on = intent.getBooleanExtra("appLock", false)
            CoroutineScope(Dispatchers.IO).launch { container.settings.update { it.copy(biometricLock = on) } }
        }
        if (intent.hasExtra("screenshots")) debugScreenshots = intent.getBooleanExtra("screenshots", false)
        if (intent.getBooleanExtra("lockNow", false)) container.appLock.lock()
        if (intent.getBooleanExtra("plainDb", false)) {
            CoroutineScope(Dispatchers.IO).launch { DebugSecurity.downgradeToPlaintext(this@MainActivity, container.database) }
        }
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
                    if (intent.getBooleanExtra("conflict", false)) DebugSeed.seedConflict(container)
                }
                VoiceDebug.runSaved(container)
            }
        }
        if (intent.getBooleanExtra("fakeDrive", false)) {
            CoroutineScope(Dispatchers.IO).launch { DebugSeed.seedDrive(container, intent.getBooleanExtra("driveRun", false)) }
        }
        CoroutineScope(Dispatchers.IO).launch { DebugAlarms.handle(this@MainActivity, container, intent) }
        CoroutineScope(Dispatchers.IO).launch { DebugWidgets.handle(this@MainActivity, container, intent) }
    }

    companion object {
        /** Opens the app straight into listening; sent by the Quick Settings tile and the shortcut. */
        const val ACTION_LISTEN = "app.cove.companion.action.LISTEN"
    }
}
