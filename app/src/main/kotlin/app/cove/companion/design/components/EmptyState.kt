package app.cove.companion.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.illustrations.SceneBanner

/** A button under an [EmptyState]: [label] and what it does. */
class EmptyAction(val label: String, val onClick: () -> Unit)

/**
 * Calm empty page: a [scene] illustration, a [title], one helper [line] and at most one primary and one secondary action.
 * Copy should be short and kind, never blaming, with no exclamation marks.
 *
 * @param fill true when the state is the whole page body: it centres in the space above the bottom bar (clearing
 * [DockClearance]) and scrolls when text or font scale needs more room. False places it inline, for use inside
 * a page that already scrolls.
 * @param compact smaller art for pages that share the screen with other content.
 */
@Composable
fun EmptyState(
    scene: Scene,
    title: String,
    line: String?,
    modifier: Modifier = Modifier,
    primary: EmptyAction? = null,
    secondary: EmptyAction? = null,
    fill: Boolean = false,
    compact: Boolean = false,
) {
    val frame = if (fill) {
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, bottom = DockClearance)
    } else modifier.fillMaxWidth().padding(vertical = 16.dp)
    Column(
        frame,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (fill) Arrangement.Center else Arrangement.Top,
    ) {
        val artHeight = if (compact) 128.dp else 176.dp
        if (scene.isTimeOfDay) SceneBanner(scene, if (compact) 120.dp else 190.dp, Modifier.fillMaxWidth())
        else Illustration(scene, Modifier.height(artHeight))
        Column(
            Modifier.padding(top = if (compact) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoveText(title, style = CoveType.Heading, textAlign = TextAlign.Center)
            if (line != null) CoveText(line, style = CoveType.Meta.copy(lineHeight = CoveType.Meta.lineHeight * 1.1f), color = Cove.colors.muted, textAlign = TextAlign.Center)
        }
        if (primary != null || secondary != null) {
            Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                primary?.let { PillButton(it.label, it.onClick) }
                secondary?.let { AccentButton(it.label, it.onClick) }
            }
        }
    }
}
