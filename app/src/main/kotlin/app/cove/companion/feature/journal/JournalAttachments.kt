package app.cove.companion.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cove.companion.ai.model.Summary
import app.cove.companion.core.shortTime
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.PlaybackState
import app.cove.companion.design.Cove
import app.cove.companion.design.moodHue
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.Chip
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.plan.OptionList

/**
 * A voice note on one row that never wraps: play/pause circle, a slim progress bar taking the remaining width, a
 * fixed-width duration ("0:42") and a 48 dp remove button. A [hint] ("Loading…") replaces the bar. At large font
 * scales the duration moves under the bar.
 */
@Composable
fun VoiceRow(note: JournalMediaEntity, playback: PlaybackState, onToggle: () -> Unit, onRemove: () -> Unit, hint: String? = null) {
    val c = Cove.colors
    val playing = playback.id == note.id
    val total = note.durationMs ?: 0
    val progress = if (playing && total > 0) (playback.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val duration = formatDuration(if (playing) playback.positionMs else total)
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val taken = if (note.updatedAt > 0) "recorded at " + shortTime(note.updatedAt.toLocalDateTime()) else ""
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(c.well, CoveShapes.Pill).padding(start = 10.dp, end = 4.dp)
            .semantics { contentDescription = "Voice note $taken, ${formatDuration(total)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(c.accent, CoveShapes.Circle).pressable(onToggle, role = Role.Button).semantics { contentDescription = if (playing) "Pause voice note" else "Play voice note" }, contentAlignment = Alignment.Center) {
            CoveIcon(if (playing) CoveIcons.Pause else CoveIcons.Play, c.onAccent, size = 16.dp)
        }
        if (stacked) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VoiceProgress(progress, hint)
                if (hint == null) VoiceDuration(duration)
            }
        } else {
            Box(Modifier.weight(1f)) { VoiceProgress(progress, hint) }
            if (hint == null) VoiceDuration(duration)
        }
        Box(Modifier.size(48.dp).pressable(onRemove, role = Role.Button).semantics { contentDescription = "Remove voice note" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.Close, c.tail, size = 14.dp)
        }
    }
}

/** Font scale from which the voice row's duration sits under the bar instead of beside it. */
private const val STACK_FONT_SCALE = 1.6f

/** The slim bar (track and filled part), or the one-line [hint] in its place. */
@Composable
private fun VoiceProgress(progress: Float, hint: String?) {
    val c = Cove.colors
    if (hint != null) {
        CoveText(hint, style = CoveType.Meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Box(Modifier.fillMaxWidth().height(4.dp).background(c.wellStrong, CoveShapes.Pill)) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.accent, CoveShapes.Pill))
    }
}

/** "0:42", single line, wide enough for "00:00" so the bar does not shift while playing. */
@Composable
private fun VoiceDuration(text: String) =
    CoveText(text, Modifier.widthIn(min = 40.dp), style = CoveType.Meta, color = Cove.colors.muted, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Clip)

/** Shown in place of the chips while recording: live time and a Stop button. */
@Composable
fun RecordingBar(elapsedMs: Long, onStop: () -> Unit) {
    val c = Cove.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(44.dp).background(c.well, CoveShapes.Pill).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(c.alert, CoveShapes.Circle))
            CoveText("Recording ${formatDuration(elapsedMs)}", style = CoveType.Meta, color = c.muted)
        }
        PillButton("Stop", onStop, Modifier.height(44.dp).semantics { contentDescription = "Stop recording" })
    }
}

/** Add-a-photo sheet: library (Photo Picker) or camera. */
@Composable
fun PhotoSheet(visible: Boolean, onDismiss: () -> Unit, onLibrary: () -> Unit, onCamera: () -> Unit) {
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
            CoveText("Add a photo", style = CoveType.Section)
            OptionList(listOf(true to "Choose from library", false to "Take a photo"), selected = null) { library ->
                if (library) onLibrary() else onCamera()
            }
        }
    }
}

/** Mood sheet: pick how the day felt; tap the chosen mood again to clear it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodSheet(visible: Boolean, mood: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
            CoveText("How did it feel?", style = CoveType.Section)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Summary.MOODS.forEach { m ->
                    Chip(m, onClick = { onPick(if (m == mood) null else m) }, selected = m == mood, container = Cove.colors.moodHue(m).tint)
                }
            }
            if (mood != null) CoveText("Tap it again to clear.", style = CoveType.Meta, color = Cove.colors.muted)
        }
    }
}

/** Calm confirmation before an entry is deleted; Undo follows on the list. */
@Composable
fun DeleteEntrySheet(visible: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
            CoveText("Delete this entry?", style = CoveType.Section)
            CoveText("Its photos and voice notes go too. You can undo this for a few seconds.", style = CoveType.Meta, color = Cove.colors.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Keep it", onDismiss, Modifier.weight(1f), height = 56.dp)
                PillButton("Delete", onDelete, kind = ButtonKind.Destructive, height = 56.dp)
            }
        }
    }
}

/** Explains why voice notes need the microphone; [canAsk] is false once Android will no longer show its prompt. */
@Composable
fun MicHelpSheet(visible: Boolean, canAsk: Boolean, onDismiss: () -> Unit, onAllow: () -> Unit, onSettings: () -> Unit) {
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
            CoveText("Voice notes need the microphone", style = CoveType.Section)
            CoveText(
                if (canAsk) "Allow it and your notes will record on this phone." else "Allow it in Settings, under Permissions, and your notes will record on this phone.",
                style = CoveType.Meta, color = Cove.colors.muted,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (canAsk) "Allow" else "Open settings", if (canAsk) onAllow else onSettings, Modifier.weight(1f), height = 56.dp)
                PillButton("Not now", onDismiss, kind = ButtonKind.Secondary, height = 56.dp)
            }
        }
    }
}

/** "0:42" for [ms] milliseconds. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
