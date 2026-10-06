package app.cove.companion.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable

/** 16 px body used for onboarding rows and copy. */
val Body16: TextStyle = CoveType.Body.copy(fontSize = 16.sp, lineHeight = 21.6.sp)

/** 15 px muted link text style. */
val Link15: TextStyle = CoveType.Button.copy(fontWeight = FontWeight.Normal)

private val ButtonText = CoveType.Button.copy(fontSize = 16.sp, lineHeight = 21.6.sp)

/**
 * Shared frame of the three setup steps: progress bars, Skip, a two-tone title, then [content].
 *
 * @param step 1-based position among the indicator steps.
 */
@Composable
fun StepFrame(
    step: Int,
    title: String,
    titleTail: String,
    onSkip: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CoveScreen {
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                StepBars(step)
                Box(Modifier.height(44.dp).pressable(onSkip).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    CoveText("Skip", style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = Cove.colors.muted)
                }
            }
            BalancedText(title, titleTail, CoveType.Title)
            content()
        }
    }
}

@Composable
private fun StepBars(step: Int) {
    val c = Cove.colors
    Row(
        Modifier.semantics { contentDescription = "Step $step of ${OnboardingStep.indicatorCount}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(OnboardingStep.indicatorCount) { i ->
            Box(Modifier.size(24.dp, 4.dp).background(if (i < step) c.ink else c.tail.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
        }
    }
}

/** Full-width 56 dp pill; ink when [enabled], otherwise the flat disabled look. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Cove.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (enabled) c.ink else c.wellStrong, RoundedCornerShape(999.dp))
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = ButtonText, color = if (enabled) c.onInk else c.tail)
    }
}

/** Centered muted text action under a [BigButton] (48 dp tall). */
@Composable
fun TextAction(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        CoveText(text, style = Link15, color = Cove.colors.muted)
    }
}

/** Centered 14 px muted helper line. */
@Composable
fun HelperText(text: String, center: Boolean = true) {
    CoveText(
        text,
        Modifier.fillMaxWidth(),
        style = CoveType.Meta.copy(lineHeight = 21.sp),
        color = Cove.colors.muted,
        textAlign = if (center) TextAlign.Center else null,
    )
}
