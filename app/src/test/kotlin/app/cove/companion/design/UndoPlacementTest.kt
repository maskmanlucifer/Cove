package app.cove.companion.design

import androidx.compose.ui.unit.dp
import app.cove.companion.design.components.UndoPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoPlacementTest {
    @Test
    fun sitsUnderTheStatusBar() {
        assertEquals(UndoPlacement.Gap + 48.dp, UndoPlacement.top(48.dp, false))
        assertTrue(UndoPlacement.top(0.dp, false) > 0.dp)
    }

    @Test
    fun screensWithAHeaderGetClearance() {
        assertEquals(UndoPlacement.top(30.dp, false) + UndoPlacement.HeaderClearance, UndoPlacement.top(30.dp, true))
    }

    @Test
    fun staysAPillOnWideScreens() {
        assertTrue(UndoPlacement.MaxWidth <= 400.dp)
    }
}
