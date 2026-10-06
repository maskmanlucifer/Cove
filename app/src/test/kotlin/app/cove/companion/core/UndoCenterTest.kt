package app.cove.companion.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoCenterTest {
    @Test fun undoRunsTheReversalAndSkipsExpiry() = runTest(UnconfinedTestDispatcher()) {
        val center = UndoCenter(this)
        val log = mutableListOf<String>()
        val item = center.post("money", "Expense deleted", onExpire = { log += "expire" }) { log += "undo" }
        center.undo(item)
        advanceTimeBy(UndoCenter.WINDOW_MS + 1)
        runCurrent()
        assertEquals(listOf("undo"), log)
        assertNull(center.current.value)
    }

    @Test fun offerBecomesFinalAfterTheWindow() = runTest(UnconfinedTestDispatcher()) {
        val center = UndoCenter(this)
        val log = mutableListOf<String>()
        center.post("journal", "Entry deleted", onExpire = { log += "expire" }) { log += "undo" }
        advanceTimeBy(UndoCenter.WINDOW_MS - 1)
        assertEquals(emptyList<String>(), log)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("expire"), log)
        assertNull(center.current.value)
    }

    @Test fun newerOfferFinalisesTheOlderOne() = runTest(UnconfinedTestDispatcher()) {
        val center = UndoCenter(this)
        val log = mutableListOf<String>()
        val first = center.post("journal", "Photo removed", onExpire = { log += "first final" }) { log += "first undo" }
        center.post("journal", "Entry deleted") { log += "second undo" }
        runCurrent()
        assertEquals(listOf("first final"), log)
        center.undo(first)
        runCurrent()
        assertEquals(listOf("first final"), log)
    }

    @Suppress("unused")
    private fun TestScope.unused() = Unit
}
