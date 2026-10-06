package app.cove.companion.feature.security

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Padlock in the design's 24 px line style (the shared icon set has none). */
internal val LockIcon: ImageVector = ImageVector.Builder("lock", 24.dp, 24.dp, 24f, 24f).apply {
    listOf("M6 11h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3").forEach {
        addPath(
            pathData = addPathNodes(it),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()
