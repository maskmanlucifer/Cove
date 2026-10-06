# Fidelity table

Mean absolute pixel difference (0-255, `tools/compare.py`, status band ignored) between each device capture (390x844dp, light, data from `DebugSeed`) and its frame in `design/ref3x`. Today scores about 2; anything above about 6 is explained. Frame 10 (dark) is measured with `--dark --evening --now 21:50`.

| Frame | Score | Note |
|---|---|---|
| 01 Today | 1.9 |  |
| 02 Listening | 4.2 | live waveform and elapsed timer |
| 03 Voice result | 0.9 | |
| 04 Plan schedule | 2.0 |  |
| 05 Plan to-dos | 1.5 | `--es plan todos` |
| 06 Task sheet | 7.2 | frame hides the Plan page behind the scrim; the sheet itself lines up (bottom 8 dp from screen edge) |
| 07 Money | 3.1 |  |
| 08 Journal | 1.2 | |
| 09 Alarm | 5.8 | live glow animation phase |
| 10 Today (dark) | 4.0 | evening variant keeps the split "Next / in 40 min" header and card position, as the frame does |
| 11 Widgets | n/a | launcher surface, not reachable in the app |
| 12 Welcome | 2.9 | |
| 13 Wake time | 1.9 | |
| 14 Mic permission | 3.7 | |
| 15 Alarm permission | 17.4 | design shows "Allowed" on the other row and a disabled Finish; the emulator already grants notifications. Copy says Cove, not Still |
| 16 Brief | 1.9 | |
| 17 Suggestion | 10.6 | emulator shows the real alarm notification over the top; seeded to-dos differ from the frame |
| 18 Why this | 8.2 | reasons text differs from the frame ("First plan at 11 am" vs seeded "Nothing planned today"); layout matches |
| 19 Partial voice | 2.1 | |
| 20 Saved + undo | 24.5 | unchanged from before: frame omits Next card, the seeded Today shows it |
| 21 Swipe / drag | 11.8 | frame is captured mid-gesture; rest matches |
| 22 Edit categories | 6.0 | frame is in rename + delete-confirm state; list and sheet position match |
| 23 Empty category | 7.1 | frame has the Personal card expanded with its empty-state prompt |
| 24 Habits | 2.8 |  |
| 25 Money logged | 2.0 | |
| 26 Journal month | 5.9 | frame shows September; the app opens on the current month |
| 27 Me | 9.2 | app keeps extra Brief city / Calendar rows (earlier work) so Body and below sit lower; Body header, Training row, spacing match |
| 28 One thing | 1.6 | |
| 29 Lock screen | n/a | OS surface (notification), not reachable in the app |
| 30 Offline | 2.8 |  |
| 31 Sync conflict | 3.4 | |
| 32 Mic off | 8.7 | frame has a different typed string and the keyboard theme is the system's |
| 33 Add expense | 12.5 | frame has "250" typed and no keyboard; capture shows the empty keypad state |
| 34 Money categories | 3.9 |  |
| 35 New category | 44.1 | frame has "Gifts" typed and the sheet over the dimmed page; capture has the on-screen keyboard open |
| 36 Category detail | 10.9 | seeded Food expenses differ from the frame; Add button kept at the frame's 112 dp |
| 37 Add to plan | 5.3 | frame's day and time (Thu 8 Oct, 4:00 pm) are fixed; app starts from now |
| 38 Alarms | 1.7 |  |
| 39 Edit alarm | 1.9 | |
| 40 New habit | 43.3 | frame has text typed and no keyboard; capture has the keyboard open |
| 41 Training | 5.1 | extra "Plan" row (editing entry point); seeded Push has three lifts, so "3 exercises"; dock drawn by the host screen |
| 42 Log a set | 5.0 | seeded Push has three lifts ("2 of 3") |
| 43 Rest | 1.9 | |
| 44 Heard you · sets | 3.7 | |
| 45 Session done | 3.8 | |
| 46 Lift history | 4.6 | chart dates come from the seed (10 Sep start), list shows more sessions |
| 47 Body weight | 3.4 | |
| 48 Progress | 5.6 | weekly bars are 7-day windows ending today (9 Sep ... Now), list ends after three lifts |

## Known deviations
- Frames 06, 18, 22, 35, 37, 39, 40 show a dimmed copy of the page behind the sheet that the design draws with a slightly different scrim and a stripped background; the sheet geometry itself matches.
- Gboard does not follow the app theme, so the keyboard stays light in dark mode.
- Morning brief row in Me shows "On" with a chevron as in frame 27 and opens a small sheet (on/off and "Play today's brief") instead of a switch row.

## Dock redesign (bottom bar)
- The five-tab dock is now a pill with the current tab plus the orb. Tapping the pill opens the designer's own expanded state, found in each frame's HTML: the five icons (active one inked with a dot) and the orb on a canvas fade. It settles away after 4 s (longer with accessibility timeouts), on a tap anywhere, on Back, or on choosing a tab. Reduce motion swaps the slide for a short fade.
- Today: headline is "Nothing before eleven." as in frame 01 when the next event is 3 h or more away; other states keep the earlier honest count wording ("Three things today."). Offline keeps the count form (frame 30). The Spent today / Habits stats row was removed by the design.
- Frames 36 and 41-48 keep their own positions; the frame-10 evening card keeps the old header.
