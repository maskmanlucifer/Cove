package app.cove.companion.feature.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.BuildConfig
import app.cove.companion.ai.AiService
import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.SpeechEngineInfo
import app.cove.companion.ai.model.SpeechEvent
import app.cove.companion.ai.speech.SpeechErrors
import app.cove.companion.ai.speech.SpeechLogBook
import app.cove.companion.container
import app.cove.companion.core.Permissions
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading
import app.cove.companion.feature.plan.PlanSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Length of the microphone test per engine. */
private const val TEST_MS = 4_000L

/**
 * Me > Voice check: which speech engines this phone offers (and why not), the microphone permission, the locale,
 * and a 4-second live test per engine showing what each returned. "Copy details" gives a content-free report; what
 * was said is never shown or copied, only how many words were heard.
 */
@Composable
fun VoiceCheckSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ai = context.container.ai
    val scope = rememberCoroutineScope()
    var engines by remember { mutableStateOf<List<SpeechEngineInfo>?>(null) }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var testing by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var micGranted by remember { mutableStateOf(Permissions.micGranted(context)) }
    var run by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(run) { engines = ai.speechEngines() }

    fun startTest() {
        job = scope.launch {
            results = emptyList()
            for (e in engines.orEmpty().filter { it.availability == Availability.Available }) {
                testing = e.ref.id
                results = results + (e.ref.id to testEngine(ai, e.ref.id) { level = it })
            }
            testing = null
            level = 0f
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micGranted = ok
        if (ok) startTest()
    }

    PlanSheet({ job?.cancel(); onDismiss() }, gap = 16, fillHeight = false) {
        SheetHeading("Voice check")
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Fact("Microphone permission", if (micGranted) "allowed" else "not allowed")
            Fact("Device locale", Locale.getDefault().toLanguageTag())
            Fact("Phone", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val list = engines
            if (list == null) SheetCaption("Checking engines…")
            else list.forEach { e ->
                Fact(
                    e.ref.id + " (" + e.ref.location.name.lowercase() + ")",
                    when (val a = e.availability) {
                        Availability.Available -> "available"
                        is Availability.Unavailable -> "not available: ${a.reason}"
                        Availability.NeedsForeground -> "needs the app on screen"
                        is Availability.NeedsConfig -> "needs ${a.what}"
                    } + e.details.joinToString("") { "\n${it.first}: ${it.second}" },
                )
            }
            results.forEach { (id, text) -> Fact("Test: $id", text) }
            if (testing != null) Fact("Listening now", "${testing}: say a few words")
            if (SpeechLogBook.recent().isNotEmpty()) Fact("Recent sessions", SpeechLogBook.recent().takeLast(8).joinToString("\n"))
        }
        LevelMeter(level)
        PillButton(
            if (testing != null) "Testing…" else "Test microphone",
            { if (testing == null) { if (micGranted) startTest() else permission.launch(Manifest.permission.RECORD_AUDIO) } },
            Modifier.fillMaxWidth(), height = 52.dp,
        )
        PillButton(
            "Copy details",
            { copy(context, report(micGranted, engines.orEmpty(), results)) },
            Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp,
        )
        PillButton("Download offline speech", { VoiceActions.openSpeechSettings(context) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
        SheetCaption("Nothing you say is stored or copied here. Only engine names, error codes and timings.")
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CoveText(label, style = CoveType.Meta, color = Cove.colors.muted)
        CoveText(value, style = CoveType.Body)
    }
}

@Composable
private fun LevelMeter(level: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).background(Cove.colors.well, CoveShapes.Pill)) {
        Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).height(10.dp).background(Cove.colors.ink, CoveShapes.Pill))
    }
}

private fun report(mic: Boolean, engines: List<SpeechEngineInfo>, results: List<Pair<String, String>>) = buildString {
    appendLine("Cove ${BuildConfig.VERSION_NAME} voice check")
    appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
    appendLine("Microphone permission: ${if (mic) "allowed" else "not allowed"}")
    engines.forEach { e ->
        appendLine("Engine ${e.ref}: ${e.availability}")
        e.details.forEach { appendLine("  ${it.first}: ${it.second}") }
    }
    results.forEach { appendLine("Test ${it.first}: ${it.second}") }
    SpeechLogBook.recent().forEach { appendLine("log: $it") }
}.trimEnd()

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Cove voice check", text))
}

/**
 * Runs one engine alone (no failover) for [TEST_MS], feeding [onLevel], and summarises what it did without any
 * transcript text.
 */
internal suspend fun testEngine(ai: AiService, id: String, onLevel: (Float) -> Unit): String {
    val session = ai.openSpeechEngine(id) ?: return "engine not found"
    val t0 = SystemClock.elapsedRealtime()
    var readyMs = -1L
    var min = 1f
    var max = 0f
    var changes = 0
    var words = 0
    var ended = "still listening when the test stopped"
    kotlinx.coroutines.coroutineScope {
        val collector = launch {
            try {
                session.events.collect { e ->
                    when (e) {
                        is SpeechEvent.Ready -> readyMs = SystemClock.elapsedRealtime() - t0
                        SpeechEvent.Began -> Unit
                        is SpeechEvent.Level -> { changes++; min = minOf(min, e.value); max = maxOf(max, e.value); onLevel(e.value) }
                        is SpeechEvent.Partial -> words = e.text.trim().split(Regex("\\s+")).size
                        is SpeechEvent.Final -> { words = e.text.trim().split(Regex("\\s+")).size; ended = "result ($words words)" }
                        is SpeechEvent.Failure -> ended = "${e.reason.name}, code ${e.code} (${SpeechErrors.describe(e.code)})"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ended = "crashed: ${e.javaClass.simpleName}"
            }
        }
        if (withTimeoutOrNull(TEST_MS) { collector.join() } == null) {
            runCatching { session.stop() }
            if (withTimeoutOrNull(2_000) { collector.join() } == null) collector.cancelAndJoin()
        }
    }
    val ready = if (readyMs >= 0) "ready after $readyMs ms" else "never became ready"
    val levels = if (changes > 0) "level %.2f to %.2f (%d changes)".format(Locale.US, min, max, changes) else "no level changes (microphone silent or not reported)"
    return "$ready; $levels; $ended"
}
