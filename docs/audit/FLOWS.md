# Flows pass (branch `feat/flows`)

Owner feedback from a real Pixel 6a. Emulator 1080x2400 @420dpi (411dp wide), seeded.

## Bottom bar motion (`CoveDock`, `DockMotion`)
- One fixed-size layout holds the card, the label and the five tab slots; only draw-phase values change, so nothing re-measures or reflows. The orb is outside it and never moves.
- The current tab's icon sits at the same x collapsed and open (the collapsed pill is anchored on that tab's slot; tabs 4 and 5 grow their label to the left so the pill never meets the orb). The other four icons slide out from it while the card grows; the label crossfades out first, the icons fade in after.
- 240 ms, ease-out `cubic-bezier(0.2, 0, 0, 1)`, no overshoot, same curve closing. Reduce motion: 150 ms plain fade, geometry snaps at the midpoint.
- Verified with `animator_duration_scale 10` and `tools/dockseq.sh` (frame contact sheets of opening, and closing while picking a tab); scales reset to 1 afterwards. Pure maths in `DockMotionTest`.

## Navigation smoothness
| Area | Before -> after |
|---|---|
| NavHost | Fade 250/200 ms, no easing, black/white could show behind fades -> one spec (`NavMotion`): enter 240 ms fade + 16 dp slide, exit 160 ms fade, pop mirrors it, same ease-out; canvas colour behind the host so no flash; voice rises 24 dp. Reduce motion: 150 ms fade |
| Tabs | `Crossfade` composed both tabs for 150 ms, scroll reset on return -> only the current tab is composed, state kept per tab by a `SaveableStateHolder`, new tab fades in 160 ms (verified: Me scroll position survives a round trip) |
| Back | Predictive back enabled (`enableOnBackInvokedCallback`); transitions work with the gesture |
| Taps | Every `nav.go/home/goReplacing` goes through `TapGate`; `back` only from a RESUMED screen (audited: no other `navigate` call exists) |
| View models | Today, Plan, Journal, Money, Habits and Alarms build their state with `flowOn(Dispatchers.Default)` so grouping/formatting no longer runs on the main thread |
| Cold start | `baseline-prof.txt` hints for navigation, design and the tab packages |

### Frame times (`tools/jank.py`, tab tour x2, scrolls, sheets, Alarms/Habits/Training; debug build, busy shared emulator host so runs vary; medians of 3)
| | p90 | p99 | janky |
|---|---|---|---|
| before | 34 ms | 105 ms | 14.0% |
| after | 27 ms | 69 ms | 10.6% |
Best runs: before p90 32 / p99 81 / 11.6%, after p90 20 / p99 48 / 9.2%. Cold first frames of Money and Journal tabs (150-450 ms in a debug build) remain the worst; release/baseline profile should help, not measured on release because debug extras are needed for seeding.

## Undo bars (`TopUndoBar`, `UndoPlacement`)
One component, top of the screen under the status bar, slide-down + fade (fade only with reduce motion), max width 360 dp, offset 56 dp under a header row so Back/close/segmented controls stay tappable. Used by `UndoHost` (Journal, Money, Training), `UndoToastHost` (voice, in `MainScreen`), Plan and Today (`PlanUndoBar`), Money review. The categories sheet shows its own copy inside the sheet window (a dialog would hide the screen's). Auto-dismiss timers unchanged.

## Simplifications (before -> after, why)
| Screen | Before | After | Why |
|---|---|---|---|
| Today | "How was today?" chips every evening from 5 pm | Only 8 pm to 4 am, hidden for the rest of that day once picked (persisted per day in `MoodMemory`, mood still written to the journal) | Asked too early and kept asking |
| Today | "Move" on the Next card | "Edit" | It opens the editor, not a mover |
| Today | Suggestion footnote "One suggestion at a time. Ignored ones disappear at noon." | Removed | Explained internals |
| Plan > To-dos | Plain grey "Edit categories" text | `AccentButton` with pencil under the list | Did not read as a button |
| Edit categories | Swipe only | Plus an explicit Delete button per row; same "move its to-dos where?" panel; Undo (restores category and to-dos) | Swipe was undiscoverable; delete had no Undo |
| Add sheet | One sheet with To-do/Event switch | To-dos "+" adds a to-do only (title, category, optional time, reminder switch only once a time is set); Schedule "+" adds an event only (title, day, time, where, repeat); event reminder row only when editing (default 30 min before) | The switch confused; fewer rows |
| Add sheet | "Add" / "Cancel" fill / "When" / "Set a time first" switch | "Save" is the single primary, "Cancel" is quiet text, "Time (Optional)" | One obvious action |
| Habits | "Every day / Some days / Weekly" (Weekly = one day) | "Every day / Some days" (old weekly habits open as Some days) | Weekly duplicated Some days |
| Habits | "After wake-up / All day / Never"; "Add habit" + grey Cancel | "From wake-up / All day / Not on Today"; "Save" + quiet Cancel | Plain words |
| Alarms | Bedtime card with two rows opening the same editor ("Screen dims 30 min before" static) | One "Wind down" row plus a caption "Dims your screen 30 minutes before." | Duplicate row |
| Alarm edit | "Sound" (hid snooze) | "Sound and snooze" | Snooze was findable only by accident |
| Me | Seven groups, 22 rows incl. brief city/calendar, spoken replies, reduce motion, photo quality, Wi-Fi only, restore, wake time, version | Day (Alarms, Habits, Morning brief, Nudges), Body, Calm (One-thing, Look and text size), Your data (Connect, Back up now, Privacy), Security, and one "Advanced settings" row (collapsed) holding the rest | Too many rows at first glance |
| Me | One-thing caption "nothing else" | "Today shows only the next thing to do, so you can focus. Turn it off here any time." | Says why and how to leave |

## Deferred
- Money and Journal first-frame cost is composition-bound; a release baseline profile generated from a real run (Macrobenchmark) would be the next step.
- Today's other cards (Next/suggestion) keep their frame layout.
