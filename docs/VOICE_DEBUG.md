# Voice debugging: "I didn't hear anything" right after tapping the mic

Reported on a Pixel 6a (Android 16, no Gemini Nano, no ML Kit Advanced speech). Nothing here could be run on a microphone; findings come from reading the code and from fakes and the emulator. Confidence is stated per cause.

## Root causes found

1. **Engine errors were turned into "no speech" (high confidence, the reported symptom).** `MlKitSpeechProvider` (ML Kit GenAI speech, alpha) answers `checkStatus() == AVAILABLE` on devices where it then errors or completes at once. It mapped that to `SpeechFailure.Other`. `VoiceViewModel.collect` ignored every failure except `PermissionDenied`, took the empty transcript and called `understand("")`, which lands on the "I didn't hear anything" frame. The router picked the first available engine once (`AiRouter.openSpeech`), so there was no failover to the Android recognizer. Same path for the Android recognizer: `NO_MATCH`, `SPEECH_TIMEOUT`, `ERROR_CLIENT`, language errors all ended as silence.
2. **No `<queries>` for `android.speech.RecognitionService` (high confidence on Android 11+).** `SpeechRecognizer.isRecognitionAvailable` returns false without it, so the Android recognizer was never a usable fallback on the 6a. Added to the manifest.
3. **Android recognizer intent (medium).** `EXTRA_LANGUAGE` was fixed to `en`, `EXTRA_PREFER_OFFLINE=true` was always set (forces offline: errors 12/13 without a language pack), no silence lengths, no calling package. Now: device locale, then en-IN, then en-US (retried on 12/13), no offline forcing, 1.5 s complete-silence, calling package set.
4. **Threading and double start (medium).** Recognizer creation now always runs on the main looper; `listen()` could cancel a session and start another before the first recognizer was destroyed (ERROR_CLIENT 5 / BUSY 8). `listen()` is now gated (`ListenGate`) and joins the previous run first.
5. **Silence was believed without evidence.** An engine that never reported audio activity could still end as "silence". Now silence is shown only when the engine was ready and the mic showed activity (levels) or the engine ran at least 2.5 s.

Checked and fine: permission is requested before `begin()` (the first listen starts only after the grant callback); QuickListen/tile re-launches go through one `voiceRequest` and the screen guards double starts now; TTS is stopped before listening; the journal voice-note recorder is not alive on the Voice screen. Mic privacy toggle on Android 12+ gives a silent but working mic: shown as "I couldn't hear the microphone" (no activity), not as silence.

## What changed

- `ai/speech/SpeechChain`: real failover. An engine that is unavailable, errors, is not ready within 3 s, or shows no audio activity within 3 s of ready hands over silently to the next. Permission failures are not retried. Words already heard win over a later error. Engines that failed recently are tried last for 10 minutes. Only when all fail does the UI see one `Failure` with the most fixable reason and every attempt.
- Engines: ML Kit, Android on-device recognizer, Android system recognizer (cloud-capable), typed last.
- Guidance (`SpeechGuidance.kt`, `TroubleView`): permission -> Allow microphone / Open settings; no recognizer or language pack -> Download offline speech + Type instead; busy -> "Another app is using the microphone"; network -> offline hint; no audio activity -> mic may be muted; true silence -> "I didn't hear anything. Try again or type it." Never shown when the recognizer did not run.
- Listening: "Getting ready" until the engine is ready, then "Say it now"; level drives the wash; partials shown live.
- Me > Voice check: engines, why not, permission, locale, language pack, 4 s test per engine with level meter, Copy details.
- Log: tag `CoveVoice` (debug builds) with one content-free line per engine run; the last 30 lines are also in the Voice check report.

## Debug extra

`--es voiceFail permission|busy|noservice|network|silence|noactivity|failover|listen|off` replaces the engines with scripted fakes so each screen can be screenshotted (`off` returns to real engines).

## If it still fails on the phone

Open Me > Voice check, tap Test microphone, then Copy details and send it. Also `tools/phone.sh logs` shows `CoveVoice` lines. Read it like this: an engine "not available" with a reason points at the recognizer or pack; "ready" but "no level changes" means the mic delivers nothing (privacy switch, another app); an error code means that engine failed with that Android code.
