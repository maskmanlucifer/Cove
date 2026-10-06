# Audit A2: Money, Journal, Voice, Brief

Branch `audit/a2`, emulator-5558 (390x844dp, Android 16 image, no Gemini Nano, no TTS engine). Source files were not modified. Paths are relative to `app/src/main/kotlin/app/cove/companion/`. Screenshots were reviewed in light and dark; 200% font scale checked on Money, Categories, Add expense, Journal, Voice.

## Summary

Counts: **P0 = 4, P1 = 14, P2 = 12**.

The areas are visually close to the design and no crash occurred anywhere (3 monkey runs, 5,500 events, one launcher crash that is not Cove; huge 24 MB photo; 12k-char journal body; process-death draft restore for Add expense works). The serious problems are in navigation and state handling: unguarded `popBackStack` (a fast double or triple tap on Save empties the back stack and leaves a permanent blank screen and duplicate expenses), journal "Delete entry" that does not delete the entry, silent dead ends when mic permission is denied or when no TTS engine exists, and large amounts that break layouts.

## P0 (crash, data loss, dead end, blocked flow)

### P0-1. Rapid taps on Save/Done/Close pop the whole back stack: blank screen with no way out, and duplicate expenses
- Where: Add expense `feature/money/ExpenseEditScreen.kt:111-118` (`scope.launch { vm.save(); nav.back() }`, no in-flight guard); `navigation/CoveNavHost.kt:54` (`back = { controller.popBackStack() }`); same pattern in `MoneyCategoryEditScreen.kt:119`, `JournalEditScreen.kt:90`.
- Repro: Money > + > type 99999999 > `adb shell 'input tap 585 1640 & input tap 585 1640 & input tap 585 1640; wait'`.
- Expected: one expense saved, return to Money. Actual: the Food category showed two identical 9,99,99,999.99 expenses (total 20,00,07,000 = 7,000 + 2 x 9,99,99,999.99), and the screen after the toast was a fully blank canvas. `dumpsys` showed Cove was still resumed with an empty NavHost. Pressing system Back exited the app. `vm.save()` uses `newId()` per call so each tap inserts a new row.
- Evidence: screenshot `after_save` (blank page, only the "Food is past its budget" toast). Journal Done double-tap did not reproduce (timing), but the code path is identical.
- Fix: disable the button after the first tap (`var saving by remember`), and make `Nav.back` a no-op unless the current entry is RESUMED (`if (controller.currentBackStackEntry?.lifecycle?.currentState == RESUMED) popBackStack()`). Also reuse a stable id for a new draft so a duplicate save is an upsert.

### P0-2. Journal "Delete entry" does not delete the entry (it deletes the attachments and keeps the text)
- Where: `feature/journal/JournalEditViewModel.kt:87-112` plus `JournalEditScreen.kt:100,160`.
- Repro: Journal > open an entry with a title and a voice note > Mood > Delete entry.
- Expected: entry gone. Actual: returns to Journal, "N entries" unchanged, the entry is still listed and opens with its text, but its voice note is gone (files and rows removed). Reproduced three times.
- Cause: `delete()` sets `persisted = false` and soft-deletes, then `nav.back()` fires `ON_STOP` which runs `vm.save()`. The blank guard is `blank && !persisted`, so a non-blank entry is re-saved (resurrected with `deletedAt = null`).
- Fix: add a `deleted` flag checked at the top of `save()`/`ensurePersisted()`/`finish()`, and cancel the debounce job in `delete()`.

### P0-3. Delete of an entry, a voice note, a photo, an expense or a category is immediate and unconfirmed; entry delete is hidden inside the Mood sheet
- Where: `JournalAttachments.kt:147` (button inside `MoodSheet`), `ExpenseEditScreen.kt:93` (red "Delete" text), `MoneyCategoryEditScreen.kt:130`, voice-note X and photo X in `JournalAttachments.kt`.
- Expected: design frame 22 shows a delete confirmation; a destructive action about a journal entry should be findable and recoverable. Actual: one tap deletes with no confirm and no Undo (expense delete does show a 5 s "Deleted / Undo" bar, but with no mention of what was deleted; category, journal entry, photo, voice note have no Undo). Deleting a category re-files its expenses to "no category" without saying so.
- Fix: confirm sheet for category/entry delete ("Delete this entry? Its photos and voice notes go too."), Undo snackbar for photo/voice removal, move "Delete entry" to an overflow in the top bar.

### P0-4. Brief with no TTS engine: player says "playing" forever, nothing is read, no explanation
- Where: `feature/brief/SpeechOut.kt:46-55` (init failure sets `ready = false`, clears the queue, nothing is reported), `BriefScreen.kt` Controls.
- Repro: `--es route brief` on this emulator (no `tts_default_synth`). The pause icon is shown, elapsed stays 0:00 for 15+ s, no message.
- Expected: "Can't read aloud on this phone. Read it as text." with the Text view offered. Actual: silent spinner-equivalent dead end.
- Fix: surface `ready == false` / `onError` as `PlayerState.error`, show a one-line banner with a "Read as text" button, and stop auto-advancing on `onError`.

## P1 (clear UX/visual defect or inconsistency)

1. **Huge totals break layout**. Category detail (`MoneyCategoryDetailScreen.kt:39-40`): "₹20,00,07,000" next to " of 9,000" renders "of 9,000" as a vertical column of single characters. Add expense: 9,99,99,999.99 wraps to two lines at 64sp ("₹9,99,99,99 / 9.99") and pushes the layout (`MoneyType.Big`, `ExpenseEditScreen.kt:144`). Fix: `maxLines = 1` with auto-shrink, or cap at 7 whole digits.
2. **Amount keypad silently caps at 8 whole digits** (`MoneyAmount.kt:10-18`): typing 99,99,99,999 drops the last digit with no feedback. Fix: a brief shake or "Largest amount is ₹9,99,99,999".
3. **Back after the keyboard in Add expense hides the keypad**: focus a note, press system Back (keyboard closes, focus stays), `noteFocused` stays true so the keypad is gone and Save floats at the screen bottom; amount cannot be changed until the note is tapped and Done pressed (`ExpenseEditScreen.kt:123`). Fix: clear focus on IME hide (`WindowInsets.isImeVisible` effect).
4. **Duplicate category names allowed and blank name is a silent no-op** (`MoneyCategoryViewModels.kt:123-139`, `MoneyCategoryEditScreen.kt:119`): a second "Food" was created without warning; Create with an empty name does nothing (no message, no field shake). Fix: inline "Name is already used" / "Add a name" hint, disable Create until valid.
5. **Deleted category's detail screen stays open as a blank page** with live Edit and "Add" (which would preselect a deleted id via `new@<id>`) (`MoneyCategoryDetailScreen.kt` after `CategoryEditViewModel.delete`). Fix: when `state.category == null && loaded`, pop back.
6. **New/Edit category sheet with keyboard open is clipped**: sheet top sits under the status bar, Create/Cancel are cut off at the keyboard edge (screenshots `cn0`, `cn1`). Create is still tappable but half hidden. Fix: `imePadding` plus `heightIn(max = screenHeight - ime)` on the sheet's scroll area.
7. **Mic permission denied in the journal editor is a silent no-op** (`JournalEditScreen.kt:97`: `if (granted) vm.startRecording()`); also silent when `startRecording()` returns false. After two denials (permanent) the chip still does nothing. Fix: show "Voice notes need the microphone" with an "Open settings" action.
8. **Leaving the editor while recording discards the recording silently** (`JournalEditViewModel.kt:205-208` `recorder.cancel()`). Repro: Voice note, wait 4 s, system Back: the new note is not attached. Fix: stop and attach on leave, or confirm.
9. **Only the latest entry per day is reachable** (`JournalViewModel.kt:41,48,59`): a day cell opens `latestByDay`, "Recent" shows 2 rows. "Write" or a voice "journal note" on a day that has an entry creates a second one and the first becomes unreachable (no list, no search UI). After the monkey run Journal said "11 entries" and listed two "Untitled". Fix: day tap opens a list when a day has more than one, and make Write on an existing day open that entry.
10. **Mood-only entries are created as "Untitled"** (`JournalEditViewModel.kt:81-97`: the blank guard ignores mood; picking a mood on a fresh editor persists an empty entry). Fix: treat mood-only as blank until text/media exists.
11. **Voice: "add to do buy milk and call the plumber" yields the to-do "Do buy milk"** (rule parser does not strip "to do"). Result shows "Two to-dos" with the wrong first title. Fix: strip `(add )?to-?do:?` in `ai/provider/rules/RuleParser.kt`.
12. **Voice: ambiguous time "wake me at 4" at 10:35 am becomes "4:00 am"** (next 4:00 is 4 pm). Draft headline says "Check the time" but the card shows no day. Fix: pick the next upcoming occurrence for bare hours and show "tomorrow" when applicable.
13. **Voice: unrecognised phrase "log a run" gets "I only caught part of that"** even though it was typed and fully read (`voice/PartialView.kt:20`); silence timeout shows "It's a bit noisy here" (line 39), which is inaccurate for silence; the Partial screen has no close button and shows an empty “…” quote. Fix: separate "I didn't understand" (typed or heard fully) from "I only caught part"; add a close X; hide the empty quote.
14. **200% font scale**: Money category card "Fun ₹2,210 · ₹210 over, no rush" overlaps (`MoneyScreen.kt:112-116`, name and value rows do not wrap); Categories "Transport ₹2,950 of ₹5,000" wraps onto two lines awkwardly; Add expense category chips squeeze "Transport" to a sliver because "+ New" is pinned (`ExpenseEditScreen.kt:169-178`); Journal calendar rows (fixed 40dp, `JournalScreen.kt` `Box(...height(40.dp))`) touch each other and list rows have no spacing. Fix: `FlowRow`/stack for the name+value rows at large scale, let calendar cell height scale, drop the pinned "+ New" to the end of the scroll at scale > 1.3.

## P2 (polish)

1. **Icon-only controls have no content descriptions** (uiautomator shows none): Add expense close, backspace (keypad), Money "+", dock voice orb, voice Stop, Brief collapse/previous/next/play-pause, journal voice play and X, photo X, back chevrons. Add `contentDescription` ("Close", "Delete digit", "Play", "Remove photo"...), `Role.Button`.
2. **Amounts are not announced as rupees**: "₹9,99,99,999.99" is spoken by symbol; set a `semantics { contentDescription = "9 crore ... rupees" }` or `stateDescription`; daily bars (`MoneyScreen.kt:84-97`) and the Brief scrubber have no semantics; Brief scrubber is not seekable.
3. **Contrast**: computed WCAG ratios. `tail` #8E9096 on canvas 2.9:1, on card 3.2:1 (used for 13sp "₹210 over, no rush", `MoneyScreen.kt:115`); `placeholder` #A4A6AB 2.2:1 light (journal calendar days without entries, "Add a note", placeholders), 3.3-3.6:1 dark. `muted` passes (4.7:1 light, 6.9:1 dark). Darken `tail` to about #767880 and `placeholder` to about #8E9096 for small text.
4. **Touch targets under 48dp**: Journal calendar cells 44x44dp; Review "Accept"/"Skip" are fine (48dp). The Delete text in Add expense is a 44dp box with text that overflows it and sits 9dp from the screen edge instead of 16dp (`ExpenseEditScreen.kt:90-97`).
5. **Keypad has no pressed feedback** (`AmountKeypad.kt:38-43` `indication = null`): taps feel dead on slower devices; add a subtle scale or tint.
6. **Money tab shows only the top four categories** (`MoneyViewModel.kt:52 take(4)`), "Other" is dropped, so rows do not sum to the headline total and there is no "See all" (Categories link is top right only).
7. **Undo copy is vague**: "Deleted" (no object), "Undone" (which command?). Undo window is 5 s (money) and 7 s (voice), short for TalkBack users; neither is a live region. A second typed "undo" would return "Nothing to undo" because `lastCommand()` ignores the `undone` flag (`data/local/dao/Daos.kt:220`), rather than stepping back. Under the frozen debug clock all commands share a timestamp, so undo-after-restart could not be verified (see below).
8. **Journal list meta is ambiguous across months**: "Sun 27 · calm" listed under October is September 27, and the card says "1 entry" while two rows show (`JournalScreen.kt` recent list). Include the month when it differs.
9. **Voice listening screen**: primary stop button is 26dp off-centre (side buttons have different widths, `voice/ListeningView.kt`); the "Voice note" label is the same for every note and shows no timestamp.
10. **Photo sheet rows** ("Choose from library", "Take a photo") use muted grey text that reads as disabled (`JournalAttachments.kt:126-135`); the Mood chips have no visible chip or selected state in dark mode and no way to clear the mood.
11. **Voice drafts format**: expense draft shows "₹250.00" while every other screen shows "₹250"; the draft action is "Change" for expenses and "Edit" for to-dos.
12. **Category subtitle** "₹30,000 a month across five" counts all categories including Other and income; the "Say these when you log by voice and Cove files it here" line under the keywords row is also true for typed notes. Silent 8-digit budget cap (`setBudget take(8)`).

## Verified working
- Money tab figures, daily bars, category rows, category detail (spent, left, "about ₹80 a day", day groups), Add expense (kind toggle, chips follow the note via the categorizer, When picker, Paid with sheet, Save label with amount, 80% heads-up toast), expense appears in Money, Today "Spent today", category detail and Categories; delete + Undo restores; 500-char style long note scrolls in one line without crashing; draft survives "Don't keep activities" (Recents restore, ₹556 kept).
- Review screen: suggestions with reasons, Accept all, Skip, scroll end padding above the sticky button, "Check with AI" offline shows a clear message and next step ("Add a Gemini key in Me, Connect services, or use a phone with on-device AI").
- Journal: month grid, Write, title/body autosave ("Saving..." then "Saved"), photo via the system picker including a 24 MB 6000x4000 JPEG (no crash, thumbnail ok), voice recording UI (timer, Stop, 0:10 note row, play control), mood sheet, Today/Journal update after a voice "journal note" and Undo removes it.
- Voice: real listening session on the emulator mic, silence timeout to a retry screen, "Type it" screen with "Listen instead", mic-off state with "Allow microphone in settings", typed commands for to-dos, expense (with budget hint "Food so far: ₹7,250 of ₹9,000"), journal note, alarm, double-tap Save all creates no duplicates, Undo chip reverts to-do/journal/expense commands within its window; created items appear in Today and Alarms.
- Brief: layout, chips, offline label path, empty-data brief, text toggle control present.
- Monkey (3 runs, about 5,500 events, seeded): no Cove crash or ANR. Dark mode parity looked right on Review, Journal, Voice, Alarms and Brief.

## Could not test
- Gemini Nano / on-device speech result paths, cloud AI categorisation success, real speech recognition accuracy (emulator has no real mic input).
- Camera capture path (no camera app), a corrupt image (picker lists only valid media; `ImageCompressor`/`JournalEditViewModel.addPhoto` have no try/catch, so a decode failure would throw inside `viewModelScope` and likely crash: code read only).
- Storage-full behaviour, TalkBack announcements (only uiautomator content-desc inspected), reduce-motion, 500 expenses in a month, month-boundary dates, 20k-character body (about 4k characters typed via adb, no jank or crash seen), undo after restart (debug clock `--es now` makes all commands share a timestamp, so `ORDER BY createdAt DESC LIMIT 1` was ambiguous; re-test with the real clock).
- Money tab in dark mode was not screenshotted on its own (other dark screens were).
