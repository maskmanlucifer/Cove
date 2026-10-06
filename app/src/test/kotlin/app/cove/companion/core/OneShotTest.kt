package app.cove.companion.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotTest {
    @Test fun fiveRapidTapsRunTheActionOnce() = runTest(UnconfinedTestDispatcher()) {
        val guard = OneShot()
        var runs = 0
        val gate = CompletableDeferred<Unit>()
        repeat(5) { guard.launch(this) { runs++; gate.await(); true } }
        gate.complete(Unit)
        assertEquals(1, runs)
        assertTrue(guard.busy)
    }

    @Test fun tapsAfterCompletionStayIgnored() = runTest(UnconfinedTestDispatcher()) {
        val guard = OneShot()
        var runs = 0
        guard.launch(this) { runs++; true }
        assertFalse(guard.launch(this) { runs++; true })
        assertEquals(1, runs)
    }

    @Test fun anUnfinishedActionCanBeTriedAgain() = runTest(UnconfinedTestDispatcher()) {
        val guard = OneShot()
        var runs = 0
        guard.launch(this) { runs++; false }
        assertFalse(guard.busy)
        guard.launch(this) { runs++; true }
        assertEquals(2, runs)
    }
}
