package app.cove.companion.feature.voice

import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.core.PermissionStep

/** What the person can do next when listening did not work. */
enum class FixAction(val label: String) {
    AllowMic("Allow microphone"),
    OpenSettings("Open settings"),
    DownloadOffline("Download offline speech"),
    TryAgain("Try again"),
    TypeInstead("Type instead"),
}

/** A calm explanation ([head] then muted [tail]), one [hint] line and two actions. */
data class Guidance(val head: String, val tail: String, val hint: String, val primary: FixAction, val secondary: FixAction)

/**
 * The honest message for a [SpeechFailure]. Silence copy is reserved for [SpeechFailure.NoMatch], which the chain only
 * reports when a recognizer really ran and the microphone delivered audio.
 *
 * @param micStep what the microphone permission currently allows; decides between "Allow microphone" and "Open settings".
 */
fun speechGuidance(failure: SpeechFailure, micStep: PermissionStep = PermissionStep.Ask): Guidance = when (failure) {
    SpeechFailure.PermissionDenied -> Guidance(
        "Cove needs the microphone.", " Allow it so I can hear you.",
        "Typing works just as well, any time.",
        if (micStep == PermissionStep.OpenSettings) FixAction.OpenSettings else FixAction.AllowMic, FixAction.TypeInstead,
    )
    SpeechFailure.NoService -> Guidance(
        "Speech isn’t set up on this phone.", " Download offline speech, or type.",
        "Cove uses your phone’s own speech recognition, which needs a language pack.",
        FixAction.DownloadOffline, FixAction.TypeInstead,
    )
    SpeechFailure.Busy -> Guidance(
        "Another app is using the microphone.", " Close it, then try again.",
        "A call, a recording or a voice assistant may still be running.",
        FixAction.TryAgain, FixAction.TypeInstead,
    )
    SpeechFailure.Network -> Guidance(
        "Speech needs the internet here.", " Offline speech works without it.",
        "Download the offline language pack once, or type it for now.",
        FixAction.DownloadOffline, FixAction.TypeInstead,
    )
    SpeechFailure.NoActivity -> Guidance(
        "I couldn’t hear the microphone.", " Check it isn’t switched off.",
        "Android’s microphone privacy switch or another app can mute it.",
        FixAction.TryAgain, FixAction.TypeInstead,
    )
    SpeechFailure.NoMatch -> Guidance(
        "I didn’t hear anything.", " Try again or type it.",
        "Holding the phone closer can help.",
        FixAction.TryAgain, FixAction.TypeInstead,
    )
    SpeechFailure.Other -> Guidance(
        "Voice isn’t working right now.", " Try again, or type it.",
        "Me > Voice check shows what went wrong.",
        FixAction.TryAgain, FixAction.TypeInstead,
    )
}

/**
 * Lets one listening run start at a time. [tryStart] returns a token or null while a run is starting or active;
 * [end] only counts for the run that owns the token, so a cancelled run finishing late cannot unlock a newer one.
 */
class ListenGate {
    private var generation = 0
    private var active = false

    /** A token for the new run, or null when one is already running (the tap is ignored). */
    @Synchronized fun tryStart(): Int? = if (active) null else { active = true; ++generation }

    /** The run owning [token] finished. */
    @Synchronized fun end(token: Int) { if (token == generation) active = false }

    /** Frees the gate right away because the current run was cancelled on purpose. */
    @Synchronized fun abort() { active = false }
}
