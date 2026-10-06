# Training

Cove plans a simple strength programme, picks each lift's weight from past sessions, and learns from what you log. Calm by design: no streaks, no badges. Code: `feature/training/` (screens, `engine/` pure logic, `voice/`), data in `data/repo/TrainingRepository.kt`.

## Data (Room version 7, `MIGRATION_6_7`)
`exercises`, `workout_plans`, `plan_days`, `workout_sessions`, `set_logs`, `body_weights` (key `day`), `training_settings` (single row). All have `updatedAt` (and `deletedAt` except settings), go through `ChangeLog.mark`, sync (`SyncTables`, `supabase/migrations/0005_training.sql`, `setup.sql` and its asset copy) and are part of backups. Weights are kilograms everywhere; `unit` only changes display and typing. `exercises.muscleGroup` is the spec's `group` (a reserved word). Plan days and sessions keep their lifts as ordered comma-separated id lists (`exerciseIds`, `skippedIds`). A session with `startedAt == null` is planned (for example after "+ Dips"), with `endedAt == null` it is running, otherwise finished.

## Plan and schedule
First run (no plan) shows a setup on the Training home: days a week (2 to 4) with weekday chips, kg or lb, starting weights for the main lifts. It creates Push (Bench press 3x8, Overhead press 3x8, Incline dumbbell 3x10-12, Triceps pushdown), Pull (Barbell row, Lat pulldown, Biceps curl) and Legs (Squat, Romanian deadlift, Leg press, Calf raise). Days rotate Push, Legs, Pull on the chosen weekdays; a skipped day leaves the same day next in line. Quick logs outside the programme ("Extra") do not move the rotation. Edit in Training > Plan: reorder, remove (undo), add from the library or built-ins, tune sets and reps, unit, rest time and weekdays. "+ Dips" adds the day's suggested accessory to a planned session for today (with undo).

## Progression (double progression, `engine/Progression.kt`)
History is replayed from the first session; sets lighter than the session's heaviest set (warm-ups) are ignored.
| Last session | Next |
|---|---|
| all working sets reached the rep goal, fixed reps (3x8) | weight + increment, "All sets hit 8." |
| rep range (3x10-12) below the top | two more reps, "Aim for 12 reps first."; at the top: weight + increment, back to the minimum |
| bodyweight, all reps hit | two more reps, "Two more reps each set." |
| a set short, last set short | hold, "Hold. Last set was 6 reps." |
| a set short, not the last | hold, "Hold. A set came up at 6 reps." |
| short again at the same weight | deload about 10 percent, rounded to the increment (halves go lighter) |
| fewer sets than planned | hold, not counted as a miss |
No history: the starting weight from settings (rounded to the increment). Session length: sets x (rest + 45 s) + 3 min a lift, to the nearest 5.

## Session flow
Home (41) > Start > Log a set (42) per set (steppers with press-and-hold repeat, tap a number to type it, tap a logged set to edit or delete with undo) > Rest (43) > ... > Session done (45) > Save plan. Finish opens a sheet: finish, skip this lift, keep going. A killed app resumes: the position comes from what is logged. The rest end time is stored (`rest/RestStore`) and an exact alarm (`RestAlarm`, falls back to inexact) buzzes softly and posts "Rest is over" on the reminders channel only when the app is not in front; Ready cancels it. Save plan writes one event per upcoming session (id `training-<date>`, title "Legs", 30 minute reminder through the normal reminder machinery, so nudge mode applies) and offers Undo.

## Voice
Rules first (`ai/provider/rules/SpokenSets.kt`, `ExerciseNames` in `ai/model`), then Nano and cloud through `AiService` with the `log_sets`, `start_workout`, `log_body_weight`, `next_workout` schema (see `docs/AI.md`). Phrases: "bench, sixty-two and a half for eight, eight and six", "thirty-seven five for eight" (during a workout the lift on screen is assumed), "three sets of eight at sixty squat", "bench 60 for 8 then 62.5 for 6", "dips 12, 10 and 8", "bench 135 pounds for 8", "start push day", "weigh in sixty-eight point four", "what's my next workout". Lifts match by alias (bench, press, ohp, row, squat, rdl, dips, pull ups ...) and one-letter slips; a lift not yet in the library is added. Sets are drafts on the frame 44 screen until Save sets (Edit changes weight and reps); undo reverses sets, sessions and weigh-ins.

## Other screens
Lift history (46: delta text, best set, 1M/3M/All chart, sessions), Body weight (47: keypad, trend sentence, chart), Progress (48: Week/Month/Year, headline from the data, body weight, sessions per week against the goal, top sets). Charts are Canvas drawings with a spoken summary. Me > Body > Training shows the next session (`LiveTrainingSummary`).

## Debug
`--ez trainingDone true` (with seed) adds today's finished Push session; `--ez trainingStart true`; `--ez trainingProgress true`; `--ei restLeft 84`; `--ez noTraining true` (shows setup); routes `training`, `training/session`, `training/rest`, `training/summary/last`, `training/lift/Bench%20press`, `training/weight`, `training/progress`.
