# Training

One calm page, not a gym app: what you lift today, your weight, a Mon-Sun plan and a simple progress screen. No programme, no setup wizard, no rest timer, no session flow. Code: `feature/training/` (page, `plan/`, `progress/`, `engine/` pure logic, `voice/`), the Today card is `feature/today/WorkoutCard.kt`, data in `data/repo/TrainingRepository.kt`.

## What the page shows (Me > Body > Training)
1. **Today's workout**: rows like "Bench press · 60 kg · 3 x 8". Tap a row to log it: weight stepper, reps per set as chips, one tap **Done as planned** while nothing changed (Save otherwise), "Just change today's weight", "Remove today's log". A check mark once logged. **Add exercise** adds to today's weekday. Nothing planned: "Nothing planned for today." with **Plan today** and a hint that the mic works too.
2. **Weight suggestion**, only here and on Today's card (see rules below): the reason in words, **Use 62.5 kg** (changes today's weight only), x to hide it for today, and "Ask Cove" when Nano or a Gemini key is available.
3. **Weight today**: last value, a 30 day sparkline, **Add weight** (numeric keypad) or Edit.
4. **Plan your week**: Monday to Sunday with the number of exercises. A day opens its editor: add (name suggestions from earlier names and common lifts, weight, sets, reps, jump), edit, delete (Undo), up/down, **Copy to other days**, **Start from Push, Pull or Legs** (optional starters for chosen weekdays, no weights).
5. **See progress**: body weight chart (1M, 3M, All) with first to latest in words, and every exercise with its weight and change since first logged ("Bench press 60 kg, up 5 kg"); tap one for its chart.
A kg/lb switch sits at the bottom; weights are stored in kg everywhere.

## Data (Room version 8, `MIGRATION_7_8`)
| Table | Holds |
|---|---|
| `plan_exercises` | the weekday template: weekday 1-7, name, weightKg, sets, reps, incrementKg, sort |
| `day_overrides` | one date's own weight for a template row (id `<epochDay>\|<planExerciseId>`) and "suggestion dismissed" |
| `exercise_logs` | what was done: one row per lift and day (id `<epochDay>\|<lowercase name>`), top weight, planned sets x reps, reps per set "8,8,6" |
| `body_weights` | one weigh-in per day (unchanged) |
| `training_settings` | the unit only (`id`, `unit`, `updatedAt`) |
All go through `ChangeLog.mark`, sync (`SyncTables`, `supabase/migrations/0007_training_simple.sql`, `setup.sql` and its asset copy) and backups. Editing today's row (Use, change weight, voice "change my bench to...") writes a `day_overrides` row; editing in the plan editor changes the weekday template. The migration keeps history (old sets become `exercise_logs`, top weight per lift and day), drops `exercises`, `workout_plans`, `plan_days`, `workout_sessions`, `set_logs` and their pending sync work, and starts with a blank plan. The old server tables are left in place (see the SQL header); old backups restore weigh-ins only.

## Suggestion rules (`engine/WeightAdvice.kt`)
Last logged day of the lift before today, first match wins; nothing for body weight lifts, with no history, once logged today, after "x", or when it would not change today's weight. Steps are per lift (2.5 kg barbell, 1 kg dumbbell, curl, pushdown by default; the "Jump" in the editor).
| Situation | Suggestion | Reason shown |
|---|---|---|
| more than 21 days since the last session | restart about 10% lighter (rounded to the step) | "It has been 3 weeks since your last session. Ease back in at 55 kg." |
| two weak sessions in a row at the same weight | deload about 10% | "Two tough sessions at 60 kg. Try a lighter 55 kg and build back up." |
| every set reached the planned reps | last weight + step | "Last time all sets done at 60 kg. Try 62.5 kg." |
| otherwise (a set short, or fewer sets) | hold (sentence only, no button) | "Last set was 6 reps. Stay at 60 kg." |
A weak session has fewer sets than planned or a set under the planned reps. Cove never changes a weight without a tap. **Ask Cove** sends only the lift, today's weight and the last six sessions (no journal content) through `AiService.adviseWorkout` and shows one sentence; the rules answer stands either way.

## Voice (Today's mic; rules first, then Nano and cloud; see `docs/AI.md`)
Everything shows the usual draft card first (the day is part of every row: "Tomorrow: Bench press · 60 kg · 3 x 8"), then Save all / Edit, a spoken confirmation when enabled, and Undo.
- Plan: "plan tomorrow bench press sixty kilos three sets of eight and overhead press forty for eight", "add squat eighty kg five by five to Thursday", "today push day: bench 60 for 8, row 50 for 10", "plan tomorrow push day" (starter lifts). Days: today, tomorrow, weekdays and abbreviations, "this Friday", "next Monday". The weekday template changes; weight, sets or reps left out keep what the lift has.
- Change today's weight: "change my bench to sixty two and a half".
- Log: "I did bench sixty-two and a half for eight, eight and six" (also "bench 60 for 8").
- Weigh in: "my weight is sixty eight point four", "log weight 68.4", "I weigh 150 pounds".
- Ask: "what's my workout today".
Lifts match by alias (`ExerciseNames`: bench, ohp, row, squat, rdl, pull ups ...) and the user's own names. Intents: `plan_exercise(day, exercise, weight?, sets?, reps?, unit?)`, `change_weight`, `log_sets`, `log_body_weight`, `next_workout`.

## Debug
`--ez seed true` plans today as a Push day (Bench press 60, Overhead press 35, Incline dumbbell press 16), Legs two days later and Pull four days later, with four weeks of history so Bench press and Incline suggest more and Overhead press holds. `--ez noTraining true` seeds no plan. Routes `training`, `training/plan/<1-7>`, `training/progress`.
