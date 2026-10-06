package app.cove.companion.feature.plan

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons used only by the Plan screens, copied from the frames. */
object PlanIcons {
    /** The thin "+" used by "Add to ..." rows (stroke 1.6, shorter arms than [app.cove.companion.design.CoveIcons.Plus]). */
    val AddRow: ImageVector = stroke("addRow", 1.6f, "M12 6v12M6 12h12")
    val AddSmall: ImageVector = stroke("addSmall", 1.8f, "M12 5v14M5 12h14")
    val ChevronUp: ImageVector = stroke("chevronUp", 2f, "M6 15l6-6 6 6")

    private fun stroke(name: String, width: Float, d: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
