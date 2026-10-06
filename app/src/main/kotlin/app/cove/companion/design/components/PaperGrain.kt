package app.cove.companion.design.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/** Switch for the paper texture behind screens. */
const val PaperGrainEnabled = true

private const val TILE = 128
private const val GRAIN_ALPHA = 0.07f

/** One tile of fine neutral noise, generated once per process and tiled; static, so it costs nothing per frame. */
private val GrainTile: ImageBitmap by lazy {
    val rnd = Random(7)
    val px = IntArray(TILE * TILE) {
        val v = rnd.nextInt(256)
        AndroidColor.argb(255, v, v, v)
    }
    Bitmap.createBitmap(px, TILE, TILE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val GrainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(GrainTile, TileMode.Repeated, TileMode.Repeated))
}

/** Overlays the faint paper grain on the receiver's bounds at [alpha]; neutral grey noise, so the base colour is kept. */
fun Modifier.paperGrain(alpha: Float = GRAIN_ALPHA): Modifier =
    if (!PaperGrainEnabled) this else drawBehind { drawRect(GrainBrush, alpha = alpha, blendMode = BlendMode.Overlay) }
