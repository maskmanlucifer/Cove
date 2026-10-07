package app.cove.companion.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test fun unfinishedSentencesGetMorePatience() {
        listOf("remind me to", "add milk and", "set an alarm for", "buy eggs,", "call mom at").forEach {
            assertEquals(it, EndpointPolicy.UNFINISHED_MS, EndpointPolicy.graceMs(it))
        }
    }

    @Test fun completeSentencesFinishQuickly() {
        listOf("set an alarm for 6 am", "I spent 250", "remind me tomorrow", "buy milk please", "Call the dentist.").forEach {
            assertEquals(it, EndpointPolicy.COMPLETE_MS, EndpointPolicy.graceMs(it))
        }
    }

    @Test fun otherSpeechUsesTheDefault() {
        assertEquals(EndpointPolicy.DEFAULT_MS, EndpointPolicy.graceMs("buy groceries"))
        assertEquals(EndpointPolicy.DEFAULT_MS, EndpointPolicy.graceMs("   "))
    }

    @Test fun patienceOrderingMakesSense() {
        assertTrue(EndpointPolicy.COMPLETE_MS < EndpointPolicy.DEFAULT_MS)
        assertTrue(EndpointPolicy.DEFAULT_MS < EndpointPolicy.UNFINISHED_MS)
        assertTrue(EndpointPolicy.MIN_LISTEN_MS < EndpointPolicy.DEFAULT_MS)
    }
}
