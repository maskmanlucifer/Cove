# Audit A1: Today, Plan, Alarms, Habits

Branch `audit/a1`, emulator-5556 (390x844dp, 3x), debug build, seeded (`--seed --now 10:35`), light and dark, font scale 1.0 and 2.0. Source under `app/src` was not modified.

## Summary

| Priority | Count |
|---|---|
| P0 (crash, data loss, dead end, blocked flow) | 6 |
| P1 (clear UX/visual defect) | 14 |
| P2 (polish) | 10 |

No crash in 4,500 monkey events (3 seeds, seeded data, `--pct-syskeys 0`), none during process recreation ("Don't keep activities") with a sheet and draft open. Rotation is locked (manifest `screenOrientation=portrait`, confirmed with `user_rotation=1`). The main structural problems are: unbounded multi-line title fields that push Save out of reach, no day navigation in Plan Schedule so events on other days vanish, permission state never surfaced in Alarms, and no accessibility semantics anywhere in these features.

Conventions: screenshots were viewed at 1170x2532; "dp" = px/3. Paths are relative to `app/src/main/kotlin/app/cove/companion/`.

---

## P0

### P0-1 Unbounded title fields hide Save/Delete (event, to-do, task, habit, alarm label)
- Where: `feature/plan/SheetKit.kt:123-146` (`TitleField`, not `singleLine`, no length cap, no scroll), used by `AddToPlanSheet.kt:104`, `TaskSheet.kt:55`, `AlarmEditScreen.kt` (LabelPage); same for `feature/habits/HabitNewScreen.kt:98` (`BasicTextField`).
- Repro: Plan > `+` > Event, type ~180 chars (`adb shell input text "Longwordy%sevent%s..."` repeated). Same in Habits `+`. 
- Expected: field capped or scrolls internally; Add/Save stay reachable. Actual: the title grows to 22 lines and fills the whole sheet; the Day/Time/Repeat/Remind rows, Add/Save and Cancel/Delete are pushed out of the sheet (uiautomator shows Save at the bottom edge, screenshot shows only text). Back first hides the keyboard, the sheet stays full of text. User must delete characters to recover; there is no sheet scroll. Today > Move (EventEditSheet) is the same component.
- Evidence: `ev_long.png`, `ev_long2.png`, `hn.png`: white sheet with 14 to 22 repeated lines and a cursor, no buttons.
- Fix: `maxLines = 3` + `.verticalScroll`/internal scroll, cap input at ~120 chars (`onValueChange = { if (it.length <= 120) ... }`), and wrap the sheet column in `verticalScroll` with `imePadding`.

### P0-2 Events on any day other than today vanish; Schedule has no day navigation
- Where: `feature/plan/PlanViewModel.kt:50-59` (Schedule is built from today only), `AddToPlanSheet.kt` (Day picker allows any date, including past).
- Repro: Plan > `+` > Event > title > Day > Wed 7 Oct > Add.
- Expected: confirmation and a way to see/edit/delete it. Actual: sheet closes with no toast; the event appears nowhere (Schedule shows today only, Today shows today only). It cannot be found, edited or deleted. Silent data black hole; looks like the save failed.
- Evidence: `tom.png` identical to the pre-add Schedule.
- Fix: add prev/next day (or a date strip) to the Schedule header, or at least show "Added for Wed 7 Oct" with a "View" action. Also warn when picking a past day.

### P0-3 Alarm permissions are never re-checked; denied notifications/exact/full-screen show no warning
- Where: `core/Permissions.kt` is used only by `feature/onboarding/OnboardingScreens.kt`, `me/BriefSettings.kt`, `brief/CalendarSource.kt`. `feature/alarms/*` never reads it.
- Repro: `adb shell pm revoke app.cove.companion android.permission.POST_NOTIFICATIONS; adb shell appops set app.cove.companion SCHEDULE_EXACT_ALARM deny; adb shell appops set app.cove.companion USE_FULL_SCREEN_INTENT deny`, then `--es route alarms --ei alarm_in_min 1`.
- Expected: banner on Alarms ("Cove can't show alarms on the lock screen. Turn on notifications.") with a Settings link. Actual: the list shows "Next in 1 min" and all switches on as if everything works; the user may get no ring UI or a silent foreground notification. `AlarmScheduler.register` also swallows `SecurityException` and silently downgrades to inexact `setAndAllowWhileIdle`.
- Evidence: `perm.png` (no warning of any kind).
- Fix: in `AlarmsScreen` observe `Permissions.*` on resume and show a calm inline notice with `Permissions.openSettings...`; surface the inexact fallback as well.

### P0-4 Habit delete is immediate with no confirm or undo (data loss incl. history)
- Where: `feature/habits/HabitNewScreen.kt:149` (`vm.delete(); nav.back()`).
- Repro: Habits > tap a card > "Delete habit". Expected: confirm or Undo (Plan and Today have Undo chips). Actual: gone instantly with all logs; the card vanishes (`hdel.png`).
- Fix: reuse `PlanUndoBar` with soft-delete (as events/to-dos do) or a confirm step.

### P0-5 Alarm delete is immediate, no confirm/undo
- Where: `feature/alarms/AlarmEditScreen.kt` `MainPage` (`vm.delete(); close()`), `AlarmEditViewModel.delete`. Same pattern. Alarms are safety-relevant (wake-ups); a mis-tap on the 104dp Delete next to Save removes it and cancels the AlarmManager entry. Fix: undo chip on the Alarms list.

### P0-6 Save/Add unreachable at 200% font with keyboard open
- Where: `AddToPlanSheet.kt` (fixed-height rows 56dp, Row for buttons), `PlanSheet` not scrollable.
- Repro: `settings put system font_scale 2.0`, Plan > `+`, keyboard opens automatically.
- Actual: sheet shows only the top 4 rows; "Where" half cut, Add/Cancel not visible (`f_add.png`). After hiding the keyboard, Add is visible but "Cancel" wraps to "Canc / el" and is clipped, and "Remind me30 min before" collides (`f_add2.png`). Users with large text cannot add with the keyboard open and must discover that Back hides it.
- Fix: scrollable sheet content, `heightIn(min=56.dp)` rows, `softWrap=false`/shorter labels or stack the buttons at large scale.

---

## P1

1. **Accessibility: no semantics at all in these features.** `grep semantics|contentDescription|toggleable` finds nothing in `feature/{today,plan,alarms,habits}` or `design/components/Controls.kt`. `CheckCircle` (`Controls.kt:39`) and `CoveSwitch` (`:57`) are plain `clickable` boxes (`pressable`, `Buttons.kt:67`): TalkBack reads nothing for the circles, no Role.Checkbox/Switch, no state ("checked", "on"), no label tying a switch to its alarm ("6:30 am, Wake up"). Icon buttons (back chevron, `+`, habit dots, drum, day chips M/T/W...) have no descriptions; the 7 habit dots expose no day or state; the alarm drum (`AlarmDrum.kt`) is drag-only with no semantics or accessibility actions (can only be changed by tapping the 40dp faded neighbours, 15 min steps). Fix: `Modifier.toggleable(role=...)`/`semantics { stateDescription }` in `CheckCircle`/`CoveSwitch`, `contentDescription` on icon buttons, `progressBarRangeInfo`/`setProgress` on the drum.
2. **Habits has no back button and the dock highlights "Plan".** Reached from Me > Habits, the screen has only a `+`; the dock shows Plan selected (`habits.png`). Only system back works. Fix: back chevron as on Alarms, keep Me selected.
3. **Enter/Done in title fields inserts newlines.** `TitleField` has `imeAction=Done` but `onDone = {}` (`AddToPlanSheet.kt:106`, `TaskSheet.kt:55`) and no `singleLine`; Gboard's check key adds a line break (`todo_add.png`/`todo_done.png`) and does not dismiss the keyboard. Newlines and leading spaces are stored: to-do title is passed untrimmed (`AddToPlanSheet.kt:130` `onAddTodo(title, ...)` vs `title.trim()` for events), so "  Buy milk\n\n" is saved. Fix: filter `\n`, trim, make Done hide the keyboard/submit.
4. **Completing a to-do on Today makes it disappear (no undo).** Today shows `todos.take(3)` (`TodayViewModel.kt:126`); ticking "Reply to Priya" re-sorts and the row vanishes and a different one slides in (`t2.png` vs seeded). No strike-through moment, no Undo chip, and the headline still says "Three things today". When 2 of 3 are done, they stay with a check (inconsistent). Fix: keep a just-completed row visible ~3s with Undo, or show a toast.
5. **Today headline count lies.** "Three things today" is `todos.size` after `take(3)` and includes done ones (`TodayViewModel.kt:121-129`); with 50 open to-dos it still says Three. Show the remaining count and a "+N more" link to Plan.
6. **Today has no empty state.** With no events and no to-dos the headline becomes "Zero things today." (`numberWords`) and nothing else. Provide a calm line ("Nothing planned. A good day to rest.") and a path to add.
7. **"Directions" can crash or no-op.** `TodayScreen.kt:204`: `startActivity(ACTION_VIEW geo:)` without `ActivityNotFoundException` handling (a device without a maps app crashes), and for an event without a place the button is shown but does nothing (`next.event?.place?.let`). Hide it when there is no place; catch and show "No maps app found."
8. **Plan schedule clips/crowds at large text.** At 200%: time labels collide with titles ("6:30Wake up", "10:30Wind down"), the now-line label is clipped (`NowLine` fixed `height(20.dp)`, `ScheduleTab.kt:85`), "Plan" title touches the segmented control (`f_plan.png`). Use `heightIn`, a min gap, and wrap the header.
9. **Alarm rows and bedtime rows clip at large text.** `AlarmsScreen.kt:105` rows OK but subtitle clipped on last row, "Screen dims 30 min before" clipped, `GentleRow` fixed `height(56.dp)` clips "Volume rises over 2 min" and drum neighbours are cut (`f_alarms.png`, `f_aedit.png`). Also Me rows break mid-word ("Alarm/s").
10. **Alarm row top padding is cramped at 1.0x.** In the first row the 6:30 numerals start ~4dp from the card top while the last row has ~20dp below the subtitle (`alarms.png`); rows look top-heavy compared with the design's 88dp rhythm. Add equal vertical padding.
11. **Task sheet "Remind me" can be on with no time.** `TaskSheet.kt`/`AddToPlanSheet.kt`: the switch is allowed while "When" is "Not set", so the reminder can never fire and nothing explains it. Disable the switch or show "Set a time first".
12. **Alarm edit: Back/scrim discards changes silently, and Save forces `enabled = true`.** `AlarmEditViewModel.save` re-enables a switched-off alarm when you merely edit the label. Keep prior enabled state; consider a "Discard changes?" only if the draft changed.
13. **Schedule rows for alarms/vitamins are not interactive and the past is only struck through.** Wake up/Vitamins/Wind down (alarms/habits) look like to-dos but do nothing on tap; done to-dos (struck) are indistinguishable from past ones ("Call mum" 6:00 struck after completing it at 10:35). Times have no am/pm so 6:30 and 6:00 sort confusingly (`plan.png`).
14. **Debug extras ignored on running app.** `MainActivity.onNewIntent` does handle debug (line 149-152) but `route`/`alarm_preview` extras were ignored when the app was already open (warning "delivered to currently running top-most instance"; screen unchanged). Tooling annoyance for auditors, mention only.

## P2

1. Tiny hit areas: faded drum neighbours are 40dp tall (`AlarmDrum.kt` `Side`), alarm day chips draw 40dp (touch 48dp OK); PlanUndoBar "Undo" label is 36x20dp visible (pill ~48dp).
2. Add to-do/event closes with no confirmation toast; the user cannot tell whether it worked, especially when the item lands on another tab/day.
3. Greeting is "Morning, Maya." at 1:40 am (`suggest late-night`, `sug.png`); use "Late" or "Night" for hours 0-4.
4. Mixed apostrophes: "brief's one thing" (straight) vs "That’s all", "What’s happening?" (curly) (`TodayScreen.kt`, offline note).
5. Tap on a to-do row text on Today does nothing (only the 22dp circle toggles); open the task sheet or toggle on row tap.
6. Habit sheet "Show on Today" cycles Never/After wake-up/All day on each tap with no picker (`HabitEditViewModel.cycleShow`); not discoverable, "Never" for Vitamins while Today counts habits.
7. Add sheet top edge sits under the status bar at 1.0x with the keyboard open (sheet top at 8dp, clock overlaps the rounded corner, `add.png`).
8. Press feedback is a 0.96 alpha only (`Buttons.kt:67`), nearly invisible; no haptics found on check/toggle/delete (design asks for haptics on completion).
9. Muted text contrast: `Edit categories`, "all clear" and `tail` text (~#9A9CA2 on #F4F4F2) is below 4.5:1 for Meta size; check `c.tail`.
10. Past days are selectable in the Day picker with no hint; Event end time before start is not validated (not exercised).

---

## Verified working

- Today: Next card (Directions with place, Move opens event editor above the keyboard), Save disabled with empty title, Delete from Today editor. Dark mode matches light; offline look ("Offline · all still works", "will sync" rows), suggestion card (Do it / Keep as is / Why?), One-thing screen render correctly. Habit toggle from Habits updates Habits (7 of 7) and persists.
- Plan: segment switch, add to-do (appears in To-dos with category count update), task sheet autosaves on dismiss, Done shows "Finished “…”" with Undo, swipe-left delete shows "Deleted …" with Undo, category cards expand/collapse, Edit categories link present, + button 48dp, sheets sit above the IME in all tested cases (not hidden by keyboard) except the 200% case above.
- Alarms: toggles re-register with AlarmManager (`dumpsys alarm | grep cove`, fire times changed 14:15 / 22:30 / 06:30 / Sat 08:00), rapid triple-tap on the switch ended in a consistent state, edit sheet drum drags minute by minute (+10 min per 50dp), ring preview screen renders with "Hold to stop" and "Snooze 9 min".
- Robustness: monkey 1500 x3 (touch/motion heavy) with no crash and no StrictMode crash; `always_finish_activities=1` + HOME + return restored the add sheet with the typed draft (rememberSaveable); rotation locked to portrait.
- Layout: 24dp side padding, dock clearance on short lists, dark parity for Today/Plan/Alarms.

## Could not test / limits

- 500 to-dos, 200 events, 100 alarms: DB is SQLCipher so no direct seeding; only code-read (all lists are plain `Column`+`verticalScroll`, no `LazyColumn`, so large lists will compose everything; likely jank, unverified).
- Emoji and RTL input (adb `input text` cannot type non-ASCII); long titles were ASCII only. Month-end/DST/midnight clock changes not run (note `TodayViewModel.today` at `:88` is fixed at VM creation, so Today does not roll over at midnight while open; Plan uses a ticker and does).
- Actual ring at fire time and notification content with permissions denied (the app clock is frozen at 10:35 while the OS clock differs; only registration and the Alarms UI were checked). TalkBack and reduce-motion behavior judged from code only. Habit new/edit long-name path tested once; new-habit empty state and Plan empty state not captured. Swipe-right (complete) and drag-to-reorder not exercised.
