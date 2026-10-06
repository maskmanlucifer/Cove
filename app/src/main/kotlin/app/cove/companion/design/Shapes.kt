package app.cove.companion.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Radii: 999 controls, 28 cards, 36 sheets. */
object CoveShapes {
    val Pill = RoundedCornerShape(999.dp)
    val Card = RoundedCornerShape(28.dp)
    val Sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val SheetFloating = RoundedCornerShape(36.dp)
    val Circle = CircleShape
}
