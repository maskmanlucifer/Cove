# UX and accessibility fixes (branch `fix/uxfix`)

Finding -> change -> where verified. Emulator 390x844dp, seeded `--now 10:35`, light/dark, font scale 1.0 and 2.0. Excluded (resilience agent): a1 P0-3, a3 P0-1..6, P1-1, 2, 5..8, 12, 15, 16 and the privacy copy.

## a1 P0
| Finding | Change | Verified |
|---|---|---|
| P0-1 Unbounded titles | `TitleField`: no newline (Done/Enter ends editing and hides the keyboard), cap `TITLE_MAX` = 120 with counter from 100, leading spaces dropped, to-do/event/alarm/habit titles trimmed on save; `PlanSheet` content scrolls and respects the status bar; habit name capped too; titles ellipsised (2 lines) in lists. Unit test `TitleInputTest`. | 180-char title on Plan add sheet: capped, Add/Cancel reachable; Done key hides keyboard |
| P0-2 Other days vanish | Floating `DayPill` (prev/next day, tap label = back to today) on Schedule; VM `shiftDay/showDay`; Now line only on today; adding for another day shows "Added for Wed 7 Oct" with a **View** action; past days labelled "· past" in the Day row | Plan schedule, next day, 2.0x |
| P0-4 Habit delete | Inline confirm ("Delete this habit and its history?", Keep it / Delete) | Habit edit sheet |
| P0-5 Alarm delete | Confirm page in the edit sheet (Keep it is primary). Delete stays beside Save to match frame 39 (confirm is the safeguard) | Alarm edit |
| P0-6 Save unreachable at 200% | Sheets scroll with IME, `heightIn(min)` rows, `widthIn(min)` buttons (Cancel no longer wraps), event editor stacks Save over Delete | 2.0x add sheet, new habit |

## a1 P1
1. Semantics: `CheckCircle` (checkbox, Done/Not done), `CoveSwitch` (switch, On/Off, label, haptic, disabled state), `Segmented` (radio, selected), `Chip`, `PillButton` (button), `pressable(role, onClickLabel)`, `ValueRow`/`SettingsRow` merged semantics; icon-only buttons labelled (back, add, close, plus, play/pause, previous/next, stop, month and stepper arrows, backspace, remove photo); day chips (full day names), habit dots (name, state), headings. Drum picker and wake wheel: `setProgress` plus custom actions (+-15 min, +-1 min). To-do rows: custom actions Mark done, Delete, Move up/down (alternatives to swipe and drag). Schedule times read with am/pm.
2. Habits: back chevron, dock highlights Me.
3. Enter inserts newlines: see P0-1; to-do title trimmed.
4. Today ticking: row stays in place with strike/check and an Undo bar for 5 s (`TodayViewModel.toggle`, pure `todayRows`, test `TodayRowsTest`); whole row toggles.
5. Headline count = open to-dos for today; "+ N more in Plan" line.
6. Empty state: "Nothing planned. Enjoy the quiet." / "Nothing else on your list." plus "Add something in Plan, or just say it."
7. Directions: hidden without a place; `ActivityNotFoundException` -> "No maps app found on this phone."
8. 2.0x schedule: label gap, `NowLine` and bars `heightIn`.
9. 2.0x alarms/bedtime/gentle rows: `heightIn`, padding, weights; wraps instead of clipping.
10. Alarm row padding: kept the design's 88 dp rhythm (frame 38 fidelity); padding now only grows rows when text is larger. Deviation by design.
11. Reminder with no time: switch disabled with "Set a time first" (task and event sheets); stored `remind` is false without a time.
12. Alarm Save no longer forces `enabled = true` for existing alarms (new alarms start on); label trimmed. Discard prompt not added (edit state is cheap to redo; no data loss).
13. Alarm rows on Schedule open the alarm editor; done vs past is still strike-through for both (design); am/pm for screen readers.
14. Debug extras: not changed (tooling note).

## a1 P2
1. Hit areas: Compose expands touch bounds to 48 dp (uiautomator shows 48x48 for 22 dp circles); `Undo` pill and Side drum rows unchanged visually. 2. Add confirmation snack for to-dos and events (Undo). 3. Greeting "Late night, Maya." before 5 am. 4. Curly apostrophe. 5. Today row tap toggles. 6. "Show on Today" is a picker (3 options), not a cycle. 7. Sheets use `statusBarsPadding`. 8. Haptic on switches (to-do rows already had it); press feedback unchanged. 9. Contrast tokens (below). 10. Past-day hint added; end-before-start validation not added (time page already clamps via pickers; not reproduced).

## a3 (non-resilience)
- P1-4 text-size control: `Segmented` weights by label width and stacks options as full-width rows when they cannot fit one line; never breaks words.
- P1-3 Supabase/Gemini/Google sheet: sheet scrolls as one, test result sits above Save/Test and scrolls into view.
- P1-9 accessibility: as a1 P1-1 (shared components, settings rows merged, headings on main titles).
- P1-10 wake wheel: `setProgress` and custom actions.
- P1-11 200% font: Me/Connect rows `padding(vertical = 12.dp)`, value stacks under label above 1.3x, statuses wrap; verified at 2.0x.
- P1-13 contrast: see below.
- P1-14 scroll under clock: top canvas gradient in `MainScreen` (invisible at rest).
- P2-4/P2-16 not changed (44 dp pill inside 48 dp tap region; widget rows are another surface).
- P1-12, P2-5..P2-17 copy and permission items belong to the resilience agent or are out of scope.

## Contrast (WCAG AA)
| Token | Before -> after | Ratio |
|---|---|---|
| new `switchOff` | wellStrong #E9E9E6 (1.2:1) -> #8A8C92 light / #686A71 dark | 3.3:1 on card, 3.05:1 on canvas; dark 3.2:1 |
| `tail` | #8E9096 -> #7B7D84; dark #6B6D73 -> #80828A | 3.7:1 on canvas (headline tail is 32 px, large text needs 3:1); dark 4.5:1 |
| `placeholder` | #A4A6AB -> #727479; dark #6A6C72 -> #8A8C93 | 4.7:1 card, 4.3:1 canvas; dark 5.1:1 |
| `dockInactive` | dark #7A7C83 -> #8A8C93 | 5.1:1 (light was 5.2:1) |
Small text that used `tail` (done rows, "Add to ...", Gentle wake caption, Answered by) now uses `muted` (>= 4.7:1). Remaining small text in `tail`: none known; large-text uses (headline, "of 3") keep it.

## Lists
`LazyColumn` for Plan Schedule, Alarms, Journal entries, Money category detail and Money review (`cardRow` helper keeps the card look). To-dos tab keeps a scrolling column (drag/reorder state needs all rows, each category shows 6 open rows until "Show all"). With 300 seeded items (`--ei bulk 300`) scroll jank on the emulator: Plan 99th percentile frame 20 ms, detail list 20 ms, alarms (60 rows) 53 ms.

## Fidelity (`tools/compare.py`, default seed; base -> now)
01 Today 2.14 -> 4.06 (honest count adds a "+ 2 more in Plan" row for the 5 seeded open to-dos, plus the darker tail grey); 04 1.73 -> 2.27; 05 1.5 -> 1.75; 27 Me 9.62 -> 9.63; 38 Alarms 2.04 -> 2.25; 39 Edit alarm 1.9 -> 2.03; 24 Habits 2.27 -> 3.08 (back chevron); 40 New habit unchanged (keyboard open frame).

## Motion
`CheckCircle` and `CoveSwitch` animations are instant under reduce motion; sheets already fade.
