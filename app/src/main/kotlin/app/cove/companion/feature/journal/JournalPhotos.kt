package app.cove.companion.feature.journal

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.PhotoResult
import app.cove.companion.data.media.photoBox
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable

private val PhotoShape = RoundedCornerShape(24.dp)

/** The entry's photos, one per row at the full width of the content column. [onOpen] gets the tapped photo's index. */
@Composable
fun JournalPhotos(photos: List<JournalMediaEntity>, onOpen: (Int) -> Unit, onRemove: (JournalMediaEntity) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        photos.forEachIndexed { i, photo -> key(photo.id) { JournalPhoto(photo, { onOpen(i) }, { onRemove(photo) }) } }
    }
}

/**
 * One full-width photo at its own aspect ratio (tall photos are centre-cropped to 1.25 x the width). Shows the
 * blurred thumbnail while the stored image decodes off the main thread, then fades the image in over 150 ms.
 * Explains a missing or unreadable file in place, with Retry.
 */
@Composable
private fun JournalPhoto(photo: JournalMediaEntity, onOpen: () -> Unit, onRemove: () -> Unit) {
    val loader = LocalContext.current.container.photoLoader
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        var retry by remember { mutableIntStateOf(0) }
        val result by produceState<PhotoResult?>(loader.peek(photo, widthPx)?.let { PhotoResult.Ready(it) }, photo.id, widthPx, retry) {
            if (value !is PhotoResult.Ready) value = null
            value = loader.load(photo, widthPx)
        }
        val ready = (result as? PhotoResult.Ready)?.bitmap
        val thumb by produceState<Bitmap?>(loader.peekThumb(photo), photo.id) { if (value == null) value = loader.thumb(photo) }
        val size by produceState(loader.knownSize(photo.id), photo.id, retry, result) { value = loader.size(photo) ?: value }
        val box = photoBox(size?.first ?: ready?.width ?: 0, size?.second ?: ready?.height ?: 0, widthPx)
        val alpha by animateFloatAsState(if (ready != null) 1f else 0f, tween(if (reduce) 0 else 150), label = "photo")
        Box(
            Modifier
                .fillMaxWidth().height(with(density) { box.heightPx.toDp() }).clip(PhotoShape).background(Cove.colors.well)
                .semantics { contentDescription = "Photo, double tap to view full screen" }
                .pressable(onOpen, role = Role.Button),
        ) {
            thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize().then(if (ready == null) Modifier.blur(12.dp) else Modifier), contentScale = ContentScale.Crop) }
            ready?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop) }
            when (result) {
                PhotoResult.Missing -> PhotoProblem("Photo not available offline", Color.Unspecified) { retry++ }
                PhotoResult.Broken -> PhotoProblem("Couldn’t open this photo", Color.Unspecified) { retry++ }
                else -> Unit
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(48.dp)
                .pressable(onRemove, role = Role.Button).semantics { contentDescription = "Remove photo" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).background(Color(0x99000000), CoveShapes.Circle), contentAlignment = Alignment.Center) {
                CoveIcon(CoveIcons.Close, Color.White, size = 14.dp)
            }
        }
    }
}

/** Centred explanation over a photo that cannot be shown, with a Retry; [tint] Unspecified uses the app colours. */
@Composable
internal fun PhotoProblem(message: String, tint: Color, onRetry: () -> Unit) {
    val c = Cove.colors
    val ink = if (tint == Color.Unspecified) c.ink else tint
    val muted = if (tint == Color.Unspecified) c.muted else tint.copy(alpha = 0.7f)
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoveText(message, style = CoveType.Meta, color = muted, textAlign = TextAlign.Center)
        Box(
            Modifier.heightIn(min = 48.dp).background(if (tint == Color.Unspecified) c.card else Color(0x26FFFFFF), CoveShapes.Pill)
                .pressable(onRetry, role = Role.Button).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { CoveText("Retry", style = CoveType.Meta, color = ink, maxLines = 1) }
    }
}
