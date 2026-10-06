package app.cove.companion.feature.journal

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.ai.model.Summary
import app.cove.companion.container
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.PlaybackState
import app.cove.companion.design.Cove
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thumbnails of the entry's photos, each with a small remove button. */
@Composable
fun PhotoStrip(photos: List<JournalMediaEntity>, onRemove: (JournalMediaEntity) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEach { photo ->
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(Cove.colors.well)) {
                rememberThumb(photo)?.let {
                    Image(it, null, Modifier.size(96.dp), contentScale = ContentScale.Crop)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                        .background(Cove.colors.scrim, CoveShapes.Circle).pressable({ onRemove(photo) }),
                    contentAlignment = Alignment.Center,
                ) { CoveIcon(CoveIcons.Close, Color.White, size = 12.dp) }
            }
        }
    }
}

/** Decodes the photo's thumbnail, fetching it first when this device does not have it; null shows the empty well. */
@Composable
private fun rememberThumb(photo: JournalMediaEntity): ImageBitmap? {
    val fetcher = LocalContext.current.container.driveKit.fetcher
    return produceState<ImageBitmap?>(null, photo.id, photo.thumbPath, photo.localPath) {
        value = withContext(Dispatchers.IO) {
            (fetcher.thumb(photo) ?: fetcher.file(photo))?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }.value
}

/** A voice note row: play/pause, "Voice note" (or a [hint] such as "Loading…"), its length ("0:42") and a remove button. */
@Composable
fun VoiceRow(note: JournalMediaEntity, playback: PlaybackState, onToggle: () -> Unit, onRemove: () -> Unit, hint: String? = null) {
    val c = Cove.colors
    val playing = playback.id == note.id
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(c.well, CoveShapes.Pill).padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(c.ink, CoveShapes.Circle).pressable(onToggle), contentAlignment = Alignment.Center) {
            CoveIcon(if (playing) CoveIcons.Pause else CoveIcons.Play, c.onInk, size = 16.dp)
        }
        CoveText(hint ?: "Voice note", Modifier.weight(1f), style = CoveType.Body.copy(fontSize = 16.sp), color = if (hint != null) c.muted else c.ink)
        CoveText(formatDuration(if (playing) playback.positionMs else note.durationMs ?: 0), style = CoveType.Meta, color = c.muted)
        Box(Modifier.size(44.dp).pressable(onRemove), contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.Close, c.tail, size = 14.dp)
        }
    }
}

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
        PillButton("Stop", onStop, Modifier.height(44.dp))
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

/** Mood sheet: pick how the day felt; existing entries can also be deleted from here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodSheet(visible: Boolean, mood: String?, canDelete: Boolean, onDismiss: () -> Unit, onPick: (String?) -> Unit, onDelete: () -> Unit) {
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
            CoveText("How did it feel?", style = CoveType.Section)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Summary.MOODS.forEach { m ->
                    Chip(m, onClick = { onPick(if (m == mood) null else m) }, selected = m == mood)
                }
            }
            if (canDelete) PillButton("Delete entry", onDelete, Modifier.align(Alignment.CenterHorizontally), kind = ButtonKind.Destructive)
        }
    }
}

/** "0:42" for [ms] milliseconds. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
