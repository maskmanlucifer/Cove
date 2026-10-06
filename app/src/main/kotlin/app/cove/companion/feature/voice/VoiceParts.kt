package app.cove.companion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.pressable

/** 44dp white circle with an X, top right of every voice frame. */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).background(Cove.colors.card, CoveShapes.Circle).pressable(onClick, role = Role.Button).semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center,
    ) { CoveIcon(CoveIcons.Close, Cove.colors.ink, size = 18.dp) }
}

/** The grey quote of what was heard, above the headline. */
@Composable
fun Quote(text: String) {
    CoveText("“$text”", style = CoveType.Meta.copy(fontSize = 15.sp, lineHeight = 22.sp), color = Cove.colors.muted)
}

/** White 28dp card whose rows are separated by a hairline. */
@Composable
fun RowsCard(rows: List<@Composable () -> Unit>) {
    Column(
        Modifier.fillMaxWidth().background(Cove.colors.card, CoveShapes.Card).padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Cove.colors.well))
            row()
        }
    }
}

/** A 64dp card row: text on the left, [trailing] on the right. */
@Composable
fun CardRow(text: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .let { if (onClick != null) it.pressable(onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(text, Modifier.weight(1f), style = CoveType.Body)
        trailing()
    }
}

/** Big dark pill plus a white pill, the bottom action pair of the result frames. */
@Composable
fun ActionPair(primary: String, onPrimary: () -> Unit, secondary: String, secondaryWidth: Int, onSecondary: () -> Unit) {
    val label = CoveType.Button.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(primary, onPrimary, Modifier.weight(1f), height = 56.dp, textStyle = label)
        PillButton(
            secondary, onSecondary, Modifier.width(secondaryWidth.dp), kind = ButtonKind.Secondary, height = 56.dp,
            horizontalPadding = 0.dp, container = Cove.colors.card, textStyle = label.copy(fontWeight = FontWeight.Normal),
        )
    }
}

/** Centered muted hint above the action pair. */
@Composable
fun Hint(text: String) {
    CoveText(text, Modifier.fillMaxWidth(), style = CoveType.Meta, color = Cove.colors.muted, textAlign = TextAlign.Center)
}
