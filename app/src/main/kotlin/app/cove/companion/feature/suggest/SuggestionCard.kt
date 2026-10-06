package app.cove.companion.feature.suggest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.OrbColors
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DialogSystemBars
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.SheetHandle
import app.cove.companion.design.components.pressable
import kotlin.math.hypot

/** Frame 17: one suggestion with its short reasons and the Do it / Keep as is / Why? choices. */
@Composable
fun SuggestionCard(decision: DecisionEntity, detail: DecisionDetail, vm: SuggestionViewModel, modifier: Modifier = Modifier) {
    val c = Cove.colors
    Box(modifier.fillMaxWidth().clip(CoveShapes.Card).background(c.card)) {
        Canvas(
            Modifier.align(Alignment.TopEnd).offset(90.dp, (-90).dp).size(240.dp)
                .blur(14.dp).alpha(if (c.isDark) 0.4f else 0.7f),
        ) { inset(30.dp.toPx()) { drawBlobs() } }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoveText("Suggestion", style = CoveType.Meta, color = c.muted)
            CoveText(decision.body, style = CoveType.Heading.copy(fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                detail.reasons.mapNotNull { it.card }.forEach { CoveText(it, style = CoveType.Meta, color = c.muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Do it", vm::accept, height = 48.dp, horizontalPadding = 20.dp)
                PillButton("Keep as is", vm::keep, kind = ButtonKind.Secondary, height = 48.dp, container = c.canvas)
                val line = c.quiet
                Box(Modifier.height(48.dp).pressable({ vm.showWhy(true) }).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    CoveText(
                        "Why?",
                        Modifier.drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
                        style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.muted,
                    )
                }
            }
        }
    }
}

/** The card's soft three-colour glow: radial blobs that fade out at 50-55% of the farthest corner. */
private fun DrawScope.drawBlobs() {
    fun blob(color: Color, cx: Float, cy: Float, stop: Float) {
        val center = Offset(size.width * cx, size.height * cy)
        val far = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
        drawCircle(Brush.radialGradient(0f to color, 1f to color.copy(alpha = 0f), center = center, radius = far * stop), radius = size.width / 2)
    }
    blob(OrbColors.Leaf, 0.50f, 0.70f, 0.55f)
    blob(OrbColors.Lilac, 0.65f, 0.50f, 0.55f)
    blob(OrbColors.Peach, 0.40f, 0.40f, 0.50f)
    blob(OrbColors.Sun, 0.58f, 0.30f, 0.42f)
}

/** Frame 18: each reason with its source, and the two ways out. Shown in a transparent dialog so it covers the dock too. */
@Composable
fun WhySheet(detail: DecisionDetail, vm: SuggestionViewModel) {
    val c = Cove.colors
    Dialog(
        onDismissRequest = { vm.showWhy(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { w ->
            SideEffect { w.setDimAmount(0f) }
        }
        DialogSystemBars()
        Box(Modifier.fillMaxSize()) {
            CoveSheet(visible = true, onDismiss = { vm.showWhy(false) }) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SheetHandle(Modifier.align(Alignment.CenterHorizontally))
                    CoveText("Why I suggested this", style = CoveType.Section.copy(lineHeight = 32.sp))
                    Column {
                        detail.reasons.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 60.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            ) {
                                CoveText(r.why, style = CoveType.Body.copy(fontSize = 16.sp))
                                CoveText(r.source, style = CoveType.Meta, color = c.muted)
                            }
                        }
                    }
                    CoveText("Nothing changes until you say so.", style = CoveType.Meta.copy(lineHeight = 21.sp), color = c.muted)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PillButton(
                            "Got it", { vm.showWhy(false) }, Modifier.fillMaxWidth(), height = 56.dp,
                            textStyle = CoveType.Button.copy(fontSize = 16.sp),
                        )
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).pressable(vm::mute),
                            contentAlignment = Alignment.Center,
                        ) { CoveText("Stop suggesting this", style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.muted) }
                    }
                }
            }
        }
    }
}
