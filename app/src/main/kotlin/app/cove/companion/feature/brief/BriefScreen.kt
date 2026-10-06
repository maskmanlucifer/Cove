package app.cove.companion.feature.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    val c = Cove.colors
    CoveScreen {
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(s, offline, nav)
            NowReading(s)
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
                Spacer(Modifier.weight(1f))
            }
            Scrubber(s)
            Controls(s, vm.player, textMode) { textMode = !textMode }
        }
    }
}

@Composable
private fun Header(s: PlayerState, offline: Boolean, nav: Nav) {
    val c = Cove.colors
    val label = "Brief · " + BriefTiming.minutesLabel(BriefTiming.totalSeconds(s.segments)) + if (offline) " · saved for offline" else ""
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(44.dp).background(c.card, CoveShapes.Circle).pressable(nav.back), contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronDown, c.ink, size = 18.dp)
        }
        CoveText(label, style = CoveType.Meta, color = c.muted)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** The design's row is wider than the screen, so its boxes shrink: 52 -> 42.1 and 76 -> 61.5 (the play button becomes an oval). */
@Composable
private fun Controls(s: PlayerState, player: BriefPlayer, textMode: Boolean, onText: () -> Unit) {
    val c = Cove.colors
    val oval = GenericShape { size, _ -> addOval(Rect(0f, 0f, size.width, size.height)) }
    val speed = if (s.speed == 1f) "1×" else "${s.speed}×"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Cell(player::cycleSpeed) { CoveText(speed, style = CoveType.Meta, color = c.muted) }
        Cell(player::previous) { CoveIcon(CoveIcons.Previous, c.ink) }
        Box(
            Modifier.size(61.5.dp, 76.dp).clip(oval).background(c.ink).pressable(player::toggle),
            contentAlignment = Alignment.Center,
        ) { CoveIcon(if (s.playing) CoveIcons.Pause else CoveIcons.Play, c.onInk) }
        Cell(player::next) { CoveIcon(CoveIcons.Next, c.ink) }
        Cell(onText) { CoveText("Text", style = CoveType.Meta, color = if (textMode) c.ink else c.muted) }
    }
}

@Composable
private fun Cell(onClick: () -> Unit, width: Dp = 42.1.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width, 52.dp).pressable(onClick), contentAlignment = Alignment.Center) { content() }
}
