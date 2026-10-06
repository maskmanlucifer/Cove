package app.cove.companion.feature.suggest

import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DecisionRulesTest {
    private val wed = LocalDateTime.of(2026, 10, 7, 9, 0)
    private val wake = AlarmEntity("w", "Wake up", 6 * 60 + 30, 0, kind = "wake")
    private val run = AlarmEntity("r", "Run", 7 * 60, 0, kind = "custom")

    private fun ctx(
        use: LocalDateTime? = wed.withHour(1).withMinute(40),
        events: List<Int> = listOf(11 * 60),
        alarms: List<AlarmEntity> = listOf(wake, run),
        confirmed: Int = 0,
        now: LocalDateTime = wed,
    ) = SuggestionContext(now, use, events, alarms, confirmed)

    @Test
    fun lateNightProducesTheFrameCopy() {
        val c = DecisionRules.lateNight(ctx())!!
        assertEquals("Late night?", c.title)
        assertEquals("Move your 7:00 run to 6 pm and sleep in till 8?", c.body)
        assertEquals(listOf("Phone was in use until 1:40 am", "Nothing planned before 11"), c.detail.reasons.mapNotNull { it.card })
        assertEquals(listOf(AlarmMove("w", 480), AlarmMove("r", 1080)), c.detail.moves)
    }

    @Test
    fun historyAddsAReasonOnlyInTheSheet() {
        val c = DecisionRules.lateNight(ctx(confirmed = 2))!!
        assertEquals("You moved runs twice before", c.detail.reasons.last().why)
        assertNull(c.detail.reasons.last().card)
    }

    @Test
    fun withoutARunOnlyTheAlarmMoves() {
        val c = DecisionRules.lateNight(ctx(alarms = listOf(wake)))!!
        assertEquals("Sleep in till 8? Your 6:30 alarm can move to 8:00.", c.body)
        assertEquals(1, c.detail.moves.size)
    }

    @Test
    fun rulesStayQuietWhenConditionsFail() {
        assertNull(DecisionRules.lateNight(ctx(use = wed.withHour(0).withMinute(30))))
        assertNull(DecisionRules.lateNight(ctx(use = null)))
        assertNull(DecisionRules.lateNight(ctx(events = listOf(9 * 60))))
        assertNull(DecisionRules.lateNight(ctx(alarms = listOf(wake.copy(minutes = 8 * 60)))))
        assertNull(DecisionRules.lateNight(ctx(alarms = listOf(wake.copy(enabled = false)))))
        assertNull(DecisionRules.lateNight(ctx(now = wed.withHour(13))))
        assertNotNull(DecisionRules.lateNight(ctx(events = emptyList())))
    }

    @Test
    fun ignoredCardsExpireAtNoon() {
        val created = wed.withHour(7).toEpochMillis()
        assertFalse(DecisionRules.expired(created, wed.withHour(11).withMinute(59).toEpochMillis()))
        assertTrue(DecisionRules.expired(created, wed.withHour(12).toEpochMillis()))
        val afternoon = wed.withHour(15).toEpochMillis()
        assertFalse(DecisionRules.expired(afternoon, wed.plusDays(1).withHour(11).toEpochMillis()))
        assertTrue(DecisionRules.expired(afternoon, wed.plusDays(1).withHour(12).toEpochMillis()))
    }

    @Test
    fun detailJsonRoundTripsAndAcceptsPlainArrays() {
        val d = DecisionRules.lateNight(ctx())!!.detail
        assertEquals(d, DecisionDetail.decode(d.encode()))
        assertEquals(listOf("a", "b"), DecisionDetail.decode("""["a","b"]""").reasons.map { it.why })
    }
}
