package app.cove.companion.ai.speech

import app.cove.companion.ai.model.SpeechFailure

/** Android `SpeechRecognizer.ERROR_*` codes as plain language and as [SpeechFailure]; pure so it can be unit-tested. */
object SpeechErrors {
    /** The failure class for a platform error [code]. */
    fun failure(code: Int): SpeechFailure = when (code) {
        6, 7 -> SpeechFailure.NoMatch
        8, 3 -> SpeechFailure.Busy
        1, 2 -> SpeechFailure.Network
        9 -> SpeechFailure.PermissionDenied
        11, 12, 13 -> SpeechFailure.NoService
        else -> SpeechFailure.Other
    }

    /** Short human text for [code]; the code itself is always shown next to it. */
    fun describe(code: Int): String = when (code) {
        1 -> "network timeout"
        2 -> "network error"
        3 -> "audio recording error"
        4 -> "recognizer server error"
        5 -> "client error (recognizer started twice or misused)"
        6 -> "no speech heard (timeout)"
        7 -> "nothing recognised"
        8 -> "recognizer busy"
        9 -> "microphone permission missing"
        10 -> "too many requests"
        11 -> "recognizer service disconnected"
        12 -> "language not supported"
        13 -> "language pack not installed"
        14 -> "cannot check language support"
        15 -> "cannot listen to download events"
        0 -> "no code"
        else -> "unknown error"
    }
}
