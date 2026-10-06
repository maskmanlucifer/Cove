package app.cove.companion.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Speech-bubble line icon for "Import from messages", drawn like the [CoveIcons] set (24 dp grid, 1.8 stroke). */
object MessageIcon {
    val Message: ImageVector = ImageVector.Builder("message", 24.dp, 24.dp, 24f, 24f).apply {
        listOf("M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-7l-4.5 3.5V17H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 10h8M8 13h5").forEach {
            addPath(
                pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}
