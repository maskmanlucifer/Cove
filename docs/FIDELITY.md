# Fidelity table

Mean absolute pixel difference (0-255, `tools/compare.py`, status band ignored) between each device capture (390x844dp, light, data from `DebugSeed`) and its frame in `design/ref3x`. Today scores about 2; anything above about 6 is explained. Frame 10 (dark) is measured with `--dark --evening --now 21:50`.

| Frame | Score | Note |
|---|---|---|
| 01 Today | 2.1 | |
| 02 Listening | 4.2 | live waveform and elapsed timer |
| 03 Voice result | 0.9 | |
| 04 Plan schedule | 1.7 | |
| 05 Plan to-dos | 1.5 | |
| 06 Task sheet | 7.2 | frame hides the Plan page behind the scrim; the sheet itself lines up (bottom 8 dp from screen edge) |
| 07 Money | 2.4 | |
| 08 Journal | 1.2 | |
| 09 Alarm | 5.8 | live glow animation phase |
| 10 Today (dark) | 2.6 | |
| 11 Widgets | n/a | launcher surface, not reachable in the app |
| 12 Welcome | 2.9 | |
| 13 Wake time | 1.9 | |
| 14 Mic permission | 3.7 | |
| 15 Alarm permission | 17.4 | design shows "Allowed" on the other row and a disabled Finish; the emulator already grants notifications. Copy says Cove, not Still |
| 16 Brief | 1.9 | |
| 17 Suggestion | 4.5 | |
| 18 Why this | 8.2 | reasons text differs from the frame ("First plan at 11 am" vs seeded "Nothing planned today"); layout matches |
| 19 Partial voice | 2.1 | |
| 20 Saved + undo | 25.2 | frame omits the Next card and stats and has five to-dos; the seeded Today shows them. Toast and New tags match |
| 21 Swipe / drag | 11.4 | frame is captured mid-gesture (row lifted, "Done" revealed); rest matches |
| 22 Edit categories | 6.0 | frame is in rename + delete-confirm state; list and sheet position match |
| 23 Empty category | 7.3 | frame has the Personal card expanded with its empty-state prompt |
| 24 Habits | 2.3 | |
| 25 Money logged | 2.0 | |
| 26 Journal month | 5.9 | frame shows September; the app opens on the current month |
| 27 Me | 4.8 | seeded values: Spoken replies, Look "System · Large" are settings the frame has but the seed does not set; first screenful matches |
| 28 One thing | 1.6 | |
| 29 Lock screen | n/a | OS surface (notification), not reachable in the app |
| 30 Offline | 2.2 | |
| 31 Sync conflict | 3.4 | |
| 32 Mic off | 8.7 | frame has a different typed string and the keyboard theme is the system's |
| 33 Add expense | 12.5 | frame has "250" typed and no keyboard; capture shows the empty keypad state |
| 34 Money categories | 5.1 | |
| 35 New category | 44.1 | frame has "Gifts" typed and the sheet over the dimmed page; capture has the on-screen keyboard open |
| 36 Category detail | 4.6 | |
| 37 Add to plan | 5.3 | frame's day and time (Thu 8 Oct, 4:00 pm) are fixed; app starts from now |
| 38 Alarms | 6.8 | the "Late night" seed also adds the 7:00 Run alarm used by frame 17 |
| 39 Edit alarm | 1.9 | |
| 40 New habit | 43.3 | frame has text typed and no keyboard; capture has the keyboard open |

## Known deviations
- Frames 06, 18, 22, 35, 37, 39, 40 show a dimmed copy of the page behind the sheet that the design draws with a slightly different scrim and a stripped background; the sheet geometry itself matches.
- Gboard does not follow the app theme, so the keyboard stays light in dark mode.
- Morning brief row in Me shows "On" with a chevron as in frame 27 and opens a small sheet (on/off and "Play today's brief") instead of a switch row.
