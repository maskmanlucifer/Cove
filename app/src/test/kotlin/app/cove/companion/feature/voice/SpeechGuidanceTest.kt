package app.cove.companion.feature.voice

import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.ai.speech.SpeechErrors
import app.cove.companion.core.PermissionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGuidanceTest {
    @Test fun permissionOffersAllowOrSettings() {
        assertEquals(FixAction.AllowMic, speechGuidance(SpeechFailure.PermissionDenied, PermissionStep.Ask).primary)
        assertEquals(FixAction.OpenSettings, speechGuidance(SpeechFailure.PermissionDenied, PermissionStep.OpenSettings).primary)
    }

    @Test fun missingRecognizerOffersOfflineDownloadAndTyping() {
        val g = speechGuidance(SpeechFailure.NoService)
        assertEquals(FixAction.DownloadOffline, g.primary)
        assertEquals(FixAction.TypeInstead, g.secondary)
    }

    @Test fun busyAndNetworkHaveTheirOwnWords() {
        assertTrue(speechGuidance(SpeechFailure.Busy).head.contains("Another app is using the microphone"))
        assertEquals(FixAction.DownloadOffline, speechGuidance(SpeechFailure.Network).primary)
    }

    @Test fun silenceCopyAppearsOnlyForTrueSilence() {
        for (f in SpeechFailure.entries) {
            val g = speechGuidance(f)
            assertEquals(f == SpeechFailure.NoMatch, g.head.contains("didn’t hear anything"))
        }
        assertEquals(FixAction.TryAgain, speechGuidance(SpeechFailure.NoMatch).primary)
    }

    @Test fun everyFailureHasTwoDistinctActions() {
        for (f in SpeechFailure.entries) assertFalse(speechGuidance(f).primary == speechGuidance(f).secondary)
    }

    @Test fun androidErrorCodesMapToFailures() {
        assertEquals(SpeechFailure.NoMatch, SpeechErrors.failure(7))
        assertEquals(SpeechFailure.NoMatch, SpeechErrors.failure(6))
        assertEquals(SpeechFailure.Busy, SpeechErrors.failure(8))
        assertEquals(SpeechFailure.Network, SpeechErrors.failure(2))
        assertEquals(SpeechFailure.PermissionDenied, SpeechErrors.failure(9))
        assertEquals(SpeechFailure.NoService, SpeechErrors.failure(13))
        assertEquals(SpeechFailure.NoService, SpeechErrors.failure(12))
        assertEquals(SpeechFailure.Other, SpeechErrors.failure(5))
        assertNotNull(SpeechErrors.describe(13))
    }

    @Test fun gateIgnoresASecondStartUntilTheRunEnds() {
        val gate = ListenGate()
        val first = gate.tryStart()
        assertNotNull(first)
        assertNull(gate.tryStart())
        gate.end(first!!)
        assertNotNull(gate.tryStart())
    }

    @Test fun lateEndOfCancelledRunDoesNotUnlockTheNewOne() {
        val gate = ListenGate()
        val old = gate.tryStart()!!
        gate.abort()
        val fresh = gate.tryStart()!!
        gate.end(old)
        assertNull(gate.tryStart())
        gate.end(fresh)
        assertNotNull(gate.tryStart())
    }
}
