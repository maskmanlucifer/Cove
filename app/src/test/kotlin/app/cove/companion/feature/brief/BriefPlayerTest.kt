package app.cove.companion.feature.brief

import app.cove.companion.data.local.entity.BriefEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSpeech : SpeechOut {
    override var listener: SpeechListener? = null
    val queue = mutableListOf<Pair<String, Float>>()
    var stops = 0

    override fun speak(id: String, text: String, rate: Float) { queue += id to rate }
    override fun stop() { stops++; queue.clear() }
    override fun shutdown() {}
}

class BriefPlayerTest {
    private val segs = listOf(BriefSegment("a", "One two. Three four."), BriefSegment("b", "Five six."))
    private val brief = BriefEntity(1, BriefCodec.encode(segs), 0)

    @Test
    fun playQueuesEverythingAndTracksStartedUtterances() {
        val fake = FakeSpeech()
        val p = BriefPlayer({ fake }, { brief })
        p.load(brief)
        p.play()
        assertEquals(3, fake.queue.size)
        val id = fake.queue[1].first
        fake.listener!!.onStart(id)
        assertEquals(0, p.state.value.index)
        assertEquals(1, p.state.value.chunk)
        fake.listener!!.onStart(fake.queue[2].first)
        assertEquals(1, p.state.value.index)
        fake.listener!!.onDone(fake.queue[2].first)
        assertTrue(p.state.value.finished)
        assertFalse(p.state.value.playing)
    }

    @Test
    fun nextSpeedAndPauseControls() {
        val fake = FakeSpeech()
        val p = BriefPlayer({ fake }, { brief })
        p.load(brief)
        p.play()
        p.next()
        assertEquals(1, p.state.value.index)
        assertEquals(1, fake.queue.size)
        p.cycleSpeed()
        assertEquals(1.25f, p.state.value.speed, 0f)
        assertEquals(1.25f, fake.queue.single().second, 0f)
        p.pause()
        assertFalse(p.state.value.playing)
        assertTrue(fake.queue.isEmpty())
        p.previous()
        assertEquals(0, p.state.value.index)
    }

    @Test
    fun staleCallbacksFromAnEarlierRunAreIgnored() {
        val fake = FakeSpeech()
        val p = BriefPlayer({ fake }, { brief })
        p.load(brief)
        p.play()
        val old = fake.queue.last().first
        p.next()
        fake.listener!!.onStart(old)
        assertEquals(1, p.state.value.index)
    }
}
