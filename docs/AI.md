# Cove AI layer

Owner's rule: use the phone's own (native) AI where available, fall back to the cloud, behind wrappers and one facade. Code is in `app/src/main/kotlin/app/cove/companion/ai/`.

## Architecture

```
feature code (voice, brief, journal search, connect)
        │  only these types: AiService, ai.model.*, AiStatus
        ▼
  AiService  (DefaultAiService)  builds typed requests, fixes the Sensitivity of each capability
        ▼
  AiRouter ── asks ──► AiPolicy   privacy, foreground, network, size caps (the ONLY place for these rules)
        │   per capability: ordered providers; skip if policy refuses or provider unavailable;
        │   fall through on failure; Busy retried once after a short backoff
        ▼
  providers (ai/provider/)
     ondevice/  NanoProvider ──► NanoClient ──► MlKitNanoClient (ML Kit Prompt API, Gemini Nano)
                MlKitSpeechProvider, AndroidSpeechProvider, UnbundledEmbeddingProvider
     cloud/     CloudProvider ──► CloudGateway ──► GeminiDirectClient | EdgeFunctionGateway (legacy)
     rules/     RuleIntentProvider (RuleParser), TypedSpeechProvider
  prompt/ one object per capability     schema/ JSON contracts and validation of model output
```

Every result is `AiResult.Ok(value, source: ProviderRef)` or `AiResult.Failed(error: AiError, tried)`. Content is never logged.

## Capabilities

| Capability (facade) | Provider order | Privacy class |
|---|---|---|
| Intent `parseIntent` | rules, Gemini Nano (foreground), Gemini cloud, legacy Edge Function | Everyday. Cloud only for transcripts up to 160 chars; journal notes from the cloud are dropped |
| Intent kinds | `set_alarm`, `change_alarm`, `add_todo`, `add_reminder`, `log_expense`, `log_habit`, `journal_note`, `query_next`, `undo_last`, and for Training `plan_exercise`, `change_weight`, `log_sets`, `log_body_weight`, `next_workout` (workout text is ordinary data) | as Intent |
| Brief `composeBriefLines` | Gemini Nano (foreground), Gemini cloud, legacy Edge Function | Everyday, non-journal facts only (keys containing "journal" are removed) |
| Speech `openSpeech` / `openTyped` | ML Kit on-device, Android on-device recognizer, Android system recognizer, typed (see Speech sessions) | Everyday. The Android recognizer counts as Cloud when the phone has no on-device model |
| Caption `captionImage` | Gemini Nano (foreground) | Journal: on-device only |
| Summary `summarize` (sentence, tags, mood) | Gemini Nano (foreground) | Journal: on-device only |
| Category `suggestCategories` | Gemini Nano (foreground), Gemini cloud | Everyday, manual and bulk only (Review screen, batches of at most 40, never in the background). Sends only note text with amounts scrubbed and the category names; notes are identified by position. Reply `{"a":[index or -1,...]}`, validated by `CategorySchema` |
| Advice `adviseWorkout` | Gemini Nano (foreground), Gemini cloud | Everyday: lift name, today's planned weight and the last six sessions only. Manual ("Ask Cove" on a weight suggestion, shown only when Nano or a Gemini key is available); one sentence `{"s":"..."}` validated by `AdviceSchema`; never changes a weight |
| Embedding `embed` | none yet (`UnbundledEmbeddingProvider`; EmbeddingGemma planned, PLAN 6a) | Journal: on-device only |

The legacy Edge Function is used only when no Gemini key is set. Cloud speech (Gemini audio) is deliberately not built: it needs raw audio capture and upload for little gain; it would be a new `SpeechProvider` with `Location.Cloud`.

## How privacy is enforced

`AiPolicy.check` is called by the router before any provider is touched:
1. Cloud provider and `Sensitivity.Journal` is `PrivacyBlocked`, whatever the capability and even if every native provider failed. Caption, summary and embedding are always called with `Journal`; the facade fixes this, callers cannot override it.
2. Cloud and an Intent transcript over 160 characters is `PrivacyBlocked`.
3. Native providers of foreground-only capabilities get `NeedsForeground` in the background (`ForegroundTracker`).
4. Cloud with no network is `Offline`; cloud with no credential reports `NeedsConfig` through the provider's own `availability()`.

Tests: `AiRouterTest`, `DefaultAiServiceTest` (a cloud provider is deliberately listed for journal capabilities and must never be called), `FeatureBoundaryTest` (no feature file may import ML Kit, Gemini or `ai.provider/prompt/schema/router/policy`).

## Errors

`Unavailable`, `NeedsForeground`, `NeedsConfig`, `Busy` (ML Kit BUSY: one retry after 400 ms, then next provider), `RateLimited` (battery quota: next provider at once), `Timeout` (router cap 20 s per call), `InvalidOutput` (schema rejected the reply), `Offline`, `PrivacyBlocked`. A provider that throws is treated as unavailable.

## Speech sessions

`openSpeech()` always returns a `SpeechChain` (`ai/speech/`): the allowed microphone engines in order, with failover. An engine that is unavailable, errors, is not ready within 3 s, or shows no audio activity (no `Began`/`Partial`/level change) within 3 s of ready hands over to the next one silently. Permission failures are never retried; words already heard beat a later error. Silence (`SpeechFailure.NoMatch`) is reported only if the engine was ready and the mic showed activity (or, for engines without levels, ran at least 2.5 s); otherwise it counts as `NoActivity` and fails over. If nobody speaks for 7 s the engine is stopped. Engines that failed in the last 10 min are tried last. When all fail, one `SpeechEvent.Failure(reason, code, attempts)` carries the most fixable reason; the UI maps it with `speechGuidance`. Only one session may run (a second collector gets `Busy`; the Voice screen also gates `listen()`).

Events: `Ready` (say it now), `Began`, `Partial`, `Level` (orb), `Final`, `Failure`. The Android engines run all recognizer calls on the main looper, use the device locale then en-IN, en-US, 1.5 s complete-silence and no offline forcing; the on-device one needs an installed pack (error 12/13 hands over). `AndroidSpeechProvider` needs the `RecognitionService` `<queries>` entry in the manifest. One content-free log line per engine run (tag `CoveVoice`, debug) and `SpeechLogBook` feed Me > Voice check. Debugging guide: `docs/VOICE_DEBUG.md`; fakes: `--es voiceFail`.

## Native API constraints (PLAN 6a)

- Prompt API (Gemini Nano) is beta, runs only while the app is on screen, about 4,000 tokens in and 255 out: prompts are budgeted in `PromptLimits` and unit-tested. Never call it from workers or widgets.
- Nano has no embedding API. ML Kit GenAI speech is alpha.
- Keep ML Kit behind `NanoClient` and the speech providers: API changes touch one file each.

## Adding a provider

1. Implement the capability interface from `ai/provider/Providers.kt` (`IntentProvider`, `BriefProvider`, `SpeechProvider`, `CaptionProvider`, `SummaryProvider`, `EmbeddingProvider`, `CategoryProvider`) with an `id`, a `Location` (Native, Cloud, Rules) and an honest `availability()`. Return `AiResult.Failed` with a typed error, do not throw.
2. Use prompts from `ai/prompt/` and validate model output with `ai/schema/`.
3. Add it to the right list in `AppContainer.ai`; the order is the priority. Credentials come through `ServiceProvider` so key changes apply live.
4. Add a fake-based test next to `NanoProviderTest`. Do not touch the policy unless a new privacy rule is needed.

Per-expense categorisation does not use AI at all; see `docs/CATEGORIZATION.md`.

## Status UI

`AiService.status()` returns, per capability, each provider's availability and which one would answer now. Me > Connect services > Gemini shows "On this phone" and "Cloud" lines from it.

SMS text is never an AI input: `docs/SMS_IMPORT.md` parsing is rules-only and on the phone. Imported expenses carry only the cleaned merchant as note, so the category cross-check above still sends note text without amounts. Payee keys and UPI handles are never sent to AI; a label the user typed is note text like any other.
