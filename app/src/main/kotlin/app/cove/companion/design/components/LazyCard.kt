package app.cove.companion.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape for row [index] of [count] in a rounded card that is built from separate lazy-list items: only the
 * first row gets the top corners and only the last the bottom ones, so the rows read as one card.
 */
fun cardRowShape(index: Int, count: Int, radius: Dp): Shape = RoundedCornerShape(
    topStart = if (index == 0) radius else 0.dp,
    topEnd = if (index == 0) radius else 0.dp,
    bottomStart = if (index == count - 1) radius else 0.dp,
    bottomEnd = if (index == count - 1) radius else 0.dp,
)

/** Background and side padding of one lazy card row; see [cardRowShape]. */
fun Modifier.cardRow(color: Color, index: Int, count: Int, radius: Dp, horizontal: Dp = 20.dp): Modifier =
    this.fillMaxWidth().background(color, cardRowShape(index, count, radius)).padding(horizontal = horizontal)
