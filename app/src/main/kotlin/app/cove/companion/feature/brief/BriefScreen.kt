package app.cove.companion.feature.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.design.components.rememberIsOffline
import app.cove.companion.navigation.Nav

/** Frame 16: the brief player. Reads the segments aloud, with chips to jump, a scrubber, speed and a text view. */
@Composable
fun BriefScreen(nav: Nav) {
    val vm = appViewModel { BriefViewModel(it) }
    val s by vm.player.state.collectAsState()
    val offline = rememberIsOffline()
    var textMode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val c = Cove.colors
    // Nothing can be spoken on this phone: show the script as text so the brief is still useful.
    LaunchedEffect(s.problem) { if (s.problem != null) textMode = true }
    CoveScreen {
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(s, offline, nav)
            NowReading(s)
            s.problem?.let { problem ->
                ReadAloudNotice(problem, onText = { textMode = true }, onSettings = { openVoiceSettings(context) }, onRetry = vm.player::play)
            }
            if (textMode) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    s.segments.forEachIndexed { i, seg ->
                        CoveText(
                            seg.text, Modifier.pressable({ vm.player.jumpTo(i) }),
                            style = CoveType.Body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                            color = if (i == s.index) c.ink else c.muted,
                        )
                    }
                }
            } else {
                Column {
                    s.segments.forEachIndexed { i, seg -> ChipRow(i, seg.title, s.index) { vm.player.jumpTo(i) } }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Illustration(Scene.Voice, Modifier.height(150.dp)) }
            }
            Scrubber(s)
            Controls(s, vm.player, textMode) { textMode = !textMode }
        }
    }
}

/** Calm explanation for a phone that cannot read aloud, with the text view as the way forward. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadAloudNotice(problem: TtsProblem, onText: () -> Unit, onSettings: () -> Unit, onRetry: () -> Unit) {
    val c = Cove.colors
    val message = when (problem) {
        TtsProblem.LanguageMissing -> "This phone has no English voice installed. You can read the brief here instead."
        TtsProblem.Timeout -> "The voice is taking too long to start. You can read the brief here instead."
        else -> "Can’t read aloud on this phone. You can read the brief here instead."
    }
    Column(
        Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(20.dp)).padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoveText(message, style = CoveType.Meta, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoticeAction("Read as text", onText)
            NoticeAction("Voice settings", onSettings)
            NoticeAction("Try again", onRetry)
        }
    }
}

@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).pressable(onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        CoveText(label, maxLines = 1, style = CoveType.Meta.copy(fontWeight = FontWeight.Medium))
    }
}

/** Opens the system's text-to-speech page, falling back to Settings when this phone has none. */
private fun openVoiceSettings(context: Context) {
    val intents = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            continue
        } catch (e: SecurityException) {
            continue
        }
    }
}

@Composable
private fun Header(s: PlayerState, offline: Boolean, nav: Nav) {
    val c = Cove.colors
    val label = "Brief · " + BriefTiming.minutesLabel(s.fixed?.second ?: BriefTiming.totalSeconds(s.segments)) + if (offline) " · saved for offline" else ""
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(44.dp).background(c.card, CoveShapes.Circle).pressable(nav.back, role = Role.Button).semantics { contentDescription = "Close brief" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronDown, c.ink, size = 18.dp)
        }
        CoveText(label, Modifier.weight(1f).padding(horizontal = 8.dp), style = CoveType.Meta, color = c.muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun NowReading(s: PlayerState) {
    val c = Cove.colors
    val text = s.current?.text.orEmpty()
    val from = s.chunks.getOrNull(s.chunk)?.start ?: 0
    val first = s.chunks.getOrNull(s.chunk)?.text?.trimEnd().orEmpty()
    val rest = text.substring((from + first.length).coerceAtMost(text.length))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoveText("Now reading · ${s.index + 1} of ${s.segments.size}", style = CoveType.Meta, color = c.muted)
        CoveText(first, rest, style = CoveType.Title.copy(fontSize = 30.sp), color = c.ink)
    }
}

@Composable
private fun ChipRow(i: Int, title: String, current: Int, onClick: () -> Unit) {
    val c = Cove.colors
    val color = when {
        i < current -> c.tail
        i == current -> c.ink
        else -> c.muted
    }
    val style = CoveType.Body.copy(fontSize = 16.sp, fontWeight = if (i == current) FontWeight.Medium else FontWeight.Normal)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoveText("${i + 1}", Modifier.width(20.dp), style = style, color = color)
        CoveText(title, style = style, color = color)
    }
}

@Composable
private fun Scrubber(s: PlayerState) {
    val c = Cove.colors
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Brief progress"
            stateDescription = "${BriefTiming.clock(s.elapsedSeconds)} of ${BriefTiming.clock(s.totalSeconds)}"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val track = if (c.isDark) c.wellStrong else Color(0xFFE4E4E1)
        Box(Modifier.fillMaxWidth().height(4.dp).background(track, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(s.progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.ink, RoundedCornerShape(2.dp)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CoveText(BriefTiming.clock(s.elapsedSeconds), style = CoveType.Meta, color = c.muted)
            CoveText(BriefTiming.clock(s.totalSeconds), style = CoveType.Meta, color = c.muted)
        }
    }
}

/** Speed, previous, play (a 64 dp circle), next, Text: equal-weight 48 dp cells so labels stay on one line at any width or font scale. */
@Composable
private fun Controls(s: PlayerState, player: BriefPlayer, textMode: Boolean, onText: () -> Unit) {
    val c = Cove.colors
    val speed = if (s.speed == 1f) "1×" else "${s.speed}×"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cell(player::cycleSpeed, Modifier.weight(1f), label = "Speed $speed") { ControlLabel(speed, c.muted) }
        Cell(player::previous, Modifier.weight(1f), label = "Previous section") { CoveIcon(CoveIcons.Previous, c.ink) }
        Box(
            Modifier.size(64.dp).background(c.ink, CoveShapes.Circle).pressable(player::toggle, role = Role.Button)
                .semantics { contentDescription = if (s.playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) { CoveIcon(if (s.playing) CoveIcons.Pause else CoveIcons.Play, c.onInk) }
        Cell(player::next, Modifier.weight(1f), label = "Next section") { CoveIcon(CoveIcons.Next, c.ink) }
        Cell(onText, Modifier.weight(1f), label = if (textMode) "Text view, on" else "Text view, off") { ControlLabel("Text", if (textMode) c.ink else c.muted) }
    }
}

@Composable
private fun ControlLabel(text: String, color: Color) =
    CoveText(text, style = CoveType.Meta, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Cell(onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, content: @Composable () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).widthIn(min = 48.dp).pressable(onClick, role = Role.Button).semantics { if (label != null) contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
