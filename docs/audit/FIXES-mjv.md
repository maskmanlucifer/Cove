# Fixes for audit A2 (Money, Journal, Voice, Brief)

Branch `fix/mjvfix`. Each line: finding -> change -> how verified (E = on emulator-5558 with screenshots reviewed, U = unit test).

## P0
- **P0-1 double submit / empty back stack** -> `Nav.back` only pops a settled (RESUMED) screen and never the last one (falls back to Main); `Nav.go/home` drop repeats within 500 ms (`TapGate`); `OneShot` in-flight guard on Add expense Save and Delete, category Create/Save/Delete, journal Done/back/Delete, habit and alarm Save/Delete; stable ids for a new expense, category and habit so a repeat is an upsert. E: 5 parallel taps on Save saved one expense and landed on Today (no blank screen); 5 taps on Create made one "Gifts". `tools/tap-stress.sh` repeats it. U: `OneShotTest`, `TapGateTest`.
- **P0-2 journal delete resurrected** -> `deleted` flag blocks autosave, ON_STOP save and finish; soft delete of entry and media (files kept until the Undo window ends). E: delete, list stays without it, Undo brings it back.
- **P0-3 deletes unconfirmed / hidden** -> "Delete" in the editor top bar with a calm sheet; "Entry deleted", "Photo removed", "Voice note removed", "Expense deleted" and "Category deleted · N expenses moved to Other" all with Undo for 8 s through `UndoCenter`/`UndoHost` (live region). Category delete confirms with the count first. Mood sheet no longer holds Delete. U: `UndoCenterTest`. E: entry delete + undo, category sheet.
- **P0-4 Brief without TTS** -> `AndroidSpeechOut` reports no engine, missing English data, 6 s init timeout, 8 s "never started speaking" watchdog and utterance errors; `PlayerState.problem` stops "playing"; the screen explains, switches to the text view and offers Read as text, Voice settings, Try again. E: TTS disabled with `pm disable-user` (re-enabled afterwards) and the emulator's crashing Google TTS both show the notice. U: `BriefPlayerTest`.

## P1
1. Huge totals -> `FitText` (single line, shrinks), crore abbreviation ("₹20 Cr") in hero and Save label, "of 9,000" never wraps. E: 9,99,99,999.99 on one line.
2. 8-digit cap -> "Largest amount is ₹9,99,99,999" shows for 2.5 s. U: `AmountInputTest`.
3. Keypad after IME dismiss -> focus clears when the keyboard hides. E: via Back.
4. Duplicate / blank category names -> inline "That name is already used" / "Add a name to continue" (`CategoryNames`). E: duplicate shown. U.
5. Deleted category detail -> closes itself once on top (`repeatOnLifecycle(RESUMED)`).
6. Category sheet under keyboard -> sheet below the status bar, only the fields scroll, Create/Cancel pinned above the keyboard. E: screenshot.
7. Mic denied -> help sheet ("Allow" or "Open settings" once Android stops asking); busy mic gets a notice. E: denied-permanently case.
8. Back while recording -> recording is kept ("Voice note kept"). E.
9. Second entry per day -> tapping a day with several entries lists them all with "Write another"; Recent meta carries the month outside this month ("Sun 27 Sep"). E.
10. Mood-only -> never "Untitled": joins the day's entry or becomes "Feeling calm"; voice-/photo-only entries are titled "Voice note"/"Photo". U.
11. "add to do buy milk" -> lead-in stripped. U + E ("Buy milk" saved once with 5 taps, Undo removes it).
12. Ambiguous times -> rule: a bare hour with no am/pm, morning/evening word or other day means the next upcoming occurrence (10:35 + "at 4" = 4:00 pm today; at 10:35 pm = 4:00 am tomorrow); "am"/"pm", 24-hour times and "tomorrow" are taken as said; moved alarms stay morning. Draft shows "Alarm for 4:00 pm today" and "Today. Check the time." U (RuleParserTest). E.
13. "log a run" -> habit tick when the habit exists; else guesses "Add a habit called Run" (new `AddHabit` intent, undoable) and the journal note. Silence says "I didn’t hear anything. Try again or type it."; typed text says "I’m not sure what you meant"; empty quote hidden; close button added. U + E.
14. 200% font -> Money rows stack, Categories rows stack, chips scroll with "+ New", calendar rows grow with the font, list rows padded. E: Money tab, Categories, Add expense, Journal.

## P2
1. Labels -> close, add, back, backspace, decimal point, delete, voice play/remove, photo remove, stop (record and listen), Brief controls, calendar days. (Dock orb is shared: left to the UX branch.)
2. Amounts -> spoken as "n rupees" on Money figure, rows, hero and Add expense; Brief scrubber has progress text. Not seekable: deferred (new interaction, not a defect).
3. Contrast tokens -> shared `design/Color.kt`: left to the UX branch.
4. Touch targets -> own buttons 48 dp (round buttons keep the 44 dp visual); Delete label aligned 16 dp from the edge. Calendar cells keep the design's 44 dp pitch: 48 dp would break frames 26/08 (deviation).
5. Keypad press feedback -> dim + scale. 
6. Money tab -> top four rows plus an "Other" remainder row so rows sum to the total (only when more rows exist). U.
7. Undo copy -> "Expense deleted", "Entry deleted", "Undone: removed 'Buy milk'"; windows 8 s (9 s voice), live regions; second "undo" steps back (query skips undone, rowid breaks ties). U.
8. Month in Recent meta -> done (item 9 above).
9. Listening stop button off-centre -> the design frame itself is off-centre: kept. Voice note rows show the time ("Voice note · 4:12 pm").
10. Photo sheet rows / dark mood chips -> shared `OptionList`/`Chip`: left to the UX branch.
11. Expense draft "₹250.00" -> frame 25 draws ".00": kept. "Change" vs "Edit": kept (frame copy).
12. Category subtitle counts categories with a budget; hint under Monthly budget when more than 8 digits are typed. "Say these when you log by voice..." kept verbatim (frame 35).

## Stress checks (emulator-5558)
5x taps on Save and Create: one row each. 10k-char journal body: typed, saved, no crash. Mic permanently denied: help sheet. Voice with no TTS / broken TTS: notice. 200% font (reset to 1.0). `adb logcat -b crash`: no Cove crash (only the emulator's own Google TTS crashing). 24 MB photo: not re-run; the photo path is now try/catch (including OutOfMemoryError) with "Couldn’t use that photo", and the audit already ran that file without a crash.

## Fidelity (`tools/compare.py`, before -> after)
07 Money 2.4 -> 3.2 (extra Other row), 33 Add expense 12.5 -> 12.7, 34 5.1 -> 5.1, 35 44.1 -> 43.8, 36 4.6 -> 4.6, 08 Journal 1.2 -> 1.4 (Delete in top bar), 26 5.9 -> 6.0, 02 Listening 4.2 -> 4.2, 03 Voice result 0.9 -> 0.9, 16 Brief 1.9 -> 1.9, 19 Partial 2.1 -> 2.2.
