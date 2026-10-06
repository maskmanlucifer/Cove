package app.cove.companion.feature.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var t = 0L
    private fun lock(enabled: Boolean = true, after: LockAfter = LockAfter.OneMinute) =
        AppLock { t }.also { it.configure(enabled, after) }

    @Test
    fun coldStartIsLocked() = assertTrue(lock().locked.value)

    @Test
    fun unlockThenShortBackgroundStaysUnlocked() {
        val l = lock().also { it.unlock() }
        l.onBackground()
        t += 59_999
        l.onForeground()
        assertFalse(l.locked.value)
    }

    @Test
    fun relocksAfterTheConfiguredTime() {
        val l = lock().also { it.unlock() }
        l.onBackground()
        t += 60_000
        l.onForeground()
        assertTrue(l.locked.value)
    }

    @Test
    fun immediatelyLocksOnAnyBackground() {
        val l = lock(after = LockAfter.Immediately).also { it.unlock() }
        l.onBackground()
        l.onForeground()
        assertTrue(l.locked.value)
    }

    @Test
    fun fiveMinuteOptionWaitsFiveMinutes() {
        val l = lock(after = LockAfter.FiveMinutes).also { it.unlock() }
        l.onBackground()
        t += 4 * 60_000
        l.onForeground()
        assertFalse(l.locked.value)
        l.onBackground()
        t += 5 * 60_000
        l.onForeground()
        assertTrue(l.locked.value)
    }

    @Test
    fun repeatedBackgroundCallsKeepTheFirstTimestamp() {
        val l = lock().also { it.unlock() }
        l.onBackground()
        t += 40_000
        l.onBackground()
        t += 30_000
        l.onForeground()
        assertTrue(l.locked.value)
    }

    @Test
    fun disabledLockNeverRelocks() {
        val l = lock(enabled = false).also { it.unlock() }
        l.onBackground()
        t += 10 * 60_000
        l.onForeground()
        assertFalse(l.locked.value)
    }

    @Test
    fun turningTheLockOnUnlocksBecauseTheUserJustAuthenticated() {
        val l = lock(enabled = false)
        assertTrue(l.locked.value)
        l.configure(true, LockAfter.OneMinute)
        assertFalse(l.locked.value)
    }

    @Test
    fun explicitLockWins() {
        val l = lock().also { it.unlock() }
        l.lock()
        assertTrue(l.locked.value)
    }

    @Test
    fun lockAfterKeysRoundTripAndFallBack() {
        LockAfter.entries.forEach { assertEquals(it, LockAfter.fromKey(it.key)) }
        assertEquals(LockAfter.OneMinute, LockAfter.fromKey("bogus"))
    }
}
