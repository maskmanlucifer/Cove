# Audit A3: onboarding, settings, global resilience, visual quality, widgets and notifications

Branch `audit/a3`, debug build 0.1.0 on emulator-5560 (1170x2532, 3x, Android 16 / API 36). App source was not modified. Paths are relative to `app/src/main/kotlin/app/cove/companion/`.

## Summary

The app is visually faithful and its feature-level error copy is good (Supabase, Gemini, Google and Drive failures all produce plain sentences with a next step). The weak point is the layer underneath: **there is no global crash handler, no safe mode and no recovery screen**. Any failure to open the encrypted database (corrupt file, missing or invalid key, bad key file) crashes the process on every launch, silently, back to the launcher. The user cannot fix it, cannot export, and cannot even be told. The same failure kills the alarm receiver, so the alarm never rings.

Two further P0s were found by exercising the real flows: **Back up now and Restore from backup both fail on every press** (Room is called on the main thread, and the raw exception text is shown to the user), and **with notifications denied a ringing alarm has no UI at all** (sound loops for 10 minutes, nothing to tap).

Counts: **P0 = 6, P1 = 16, P2 = 17.** Monkey (3 seeds x 1500 events) was stable, cold start is about 450 ms, widgets and notification actions work.

## P0 (crash, data loss, dead end, blocked flow)

### P0-1 No global exception handler; database failures crash-loop on launch
- Where: `CoveApp.kt:31` (`appScope.launch(Dispatchers.IO) { prepareDatabase(); startServices() }`, no `CoroutineExceptionHandler`), `AppContainer.kt:240` (`appScope` has no handler), `security/EncryptedDatabase.kt:37` (`real` lazy opens key + file). `grep -rn "UncaughtExceptionHandler\|CoroutineExceptionHandler"` returns nothing.
- Repro (all done on emulator, each one then relaunched 2-3 times):
  1. Corrupt DB: `adb shell run-as app.cove.companion sh -c 'head -c 4096 /dev/urandom > databases/cove.db'`, launch.
  2. Missing key: `run-as ... rm no_backup/cove.key` with a valid DB, launch.
  3. Invalid key file: `head -c 92 /dev/urandom > no_backup/cove.key`, launch.
  4. Corrupt plaintext (header `SQLite format 3\0` + garbage) to hit the migrator, launch.
- Expected: a recovery screen that says what happened and offers a way out.
- Actual: the process dies within ~1 s and the user is dropped on the launcher with no dialog. Every launch repeats it (`pid` empty, 1 FATAL EXCEPTION per start). Exceptions seen: `SQLiteNotADatabaseException: file is not a database (code 26)` (cases 1, 2, 4) and `KeyStoreException: Signature/MAC verification failed` (case 3). Case 2 is worse: `DatabasePassphrase.get()` silently generates a new key when the file is missing, so a lost key file bricks the old data without any message.
- Evidence: `adb logcat -b crash -d` after each launch; screenshot of the launcher after launch.
- Fix: see Resilience design proposal (`DatabaseGuard` + `RecoveryActivity` + `CrashNote`). Minimum viable: wrap `prepareDatabase()` in try/catch and flip a `dbFailed` StateFlow that `MainActivity` renders as a recovery screen.

### P0-2 Alarm receiver, boot receiver and widgets die with the database
- Where: `feature/alarms/AlarmReceiver.kt` (`container.database.alarms().get(id)`), `BootReceiver.kt`, `NudgeReceiver.kt`, `WidgetUpdater.kt`, `WidgetRefreshWorker`. They only use try/finally around `goAsync`.
- Repro: same corruption as P0-1, then fire an alarm (`--ei alarm_in_min 1`) or send BOOT_COMPLETED.
- Expected: the alarm still rings (generic "Alarm" with default tone, snooze/stop) even when the DB cannot be read.
- Actual (by code path, the same crash was proven on the launch path): the receiver throws on the first query, the process dies and the alarm does not ring. A user who relies on the alarm oversleeps and gets no hint why.
- Fix: in `AlarmReceiver.fire` catch `Exception` and call `AlarmRingService.start` with a fallback `RingState("", "Alarm", minutes-from-intent, 9)`. Put `minutes`/`label` in the PendingIntent extras at schedule time so the fallback is accurate. Catch in the other receivers and log to `CrashNote`.

### P0-3 Back up now and Restore from backup always fail (main-thread Room access)
- Where: `feature/me/MeViewModel.kt:68-80` (`runBackup` launches in `viewModelScope`, i.e. Main), `data/sync/RoomSyncStore.kt:52` (`count` uses `db.query(SimpleSQLiteQuery)` blocking), `data/backup/BackupStore.kt:25` (`isEmpty`), `BackupService.backUp/restoreLatest`.
- Repro: `--ez fakeDrive true` (folder-backed Drive, `enabled` is true), Me > Back up now > Back up now. Same for Restore from backup > Restore.
- Expected: "Backed up." / "Restored N items".
- Actual: sheet shows `Something went wrong: Cannot access database on the main thread since it may potentially lock the UI for a long period of time.` Both buttons, every time. The monthly worker path works (it runs off-main), which is why `driveRun` from DebugSeed (IO thread) succeeded. The real-Drive path runs the same repository code, so this should fail identically for a signed-in user.
- Also: the raw exception message is shown (see P1-5).
- Fix: `viewModelScope.launch(Dispatchers.IO) { ... }` in `runBackup`, or `withContext(Dispatchers.IO)` inside `BackupService`/`RoomBackupStore`. Add an instrumented test that calls both from Main.

### P0-4 Restore is refused after onboarding (the only recovery path from a reinstall)
- Where: `data/backup/BackupStore.kt:25` (`isEmpty` = every non-settings table has zero rows), `feature/onboarding/OnboardingViewModel.kt` (`saveWakeTime` inserts a "Wake up" alarm on the Wake step), `BackupService.restoreLatest` (`NotEmpty`).
- Repro: fresh install, onboarding with Continue on the wake step, then Me > Restore from backup (with a valid backup).
- Expected: restore works on a freshly set-up app.
- Actual (by code; cannot be reached while P0-3 stands): the default alarm makes the store non-empty, so the user gets "Restore only works on a fresh Cove with no entries yet." The "I already use Cove" route avoids the wake step but then ends in Welcome again (P1-1) so users finish onboarding anyway.
- Fix: treat rows with `kind = "wake"` created by onboarding (or any rows with `createdAt` after install and `updatedAt == createdAt`) as empty, or restore with merge-by-id instead of refusing. Better: offer Restore inside onboarding ("I already use Cove" -> Connect -> Restore) before any default row is written.

### P0-5 Notifications denied: a ringing alarm has no screen and no notification
- Where: `feature/alarms/AlarmRingService.kt:99` (ring UI is only reachable via the notification's `setFullScreenIntent`/content intent); `MainActivity` never observes `AlarmRingService.ringing` (only `AlarmRingActivity` does).
- Repro: `pm revoke app.cove.companion android.permission.POST_NOTIFICATIONS`, `--ei alarm_in_min 1`, wait. (This state is reachable in onboarding: Notifications > Don't allow, then Finish is still enabled.)
- Expected: the ring screen or at least an in-app way to stop/snooze.
- Actual: `dumpsys activity services` shows `AlarmRingService` running, audio focus held with USAGE_ALARM, but `topResumedActivity` stays on MainActivity/Today and the notification shade has no Cove entry (screenshot). The tone and vibration loop for up to 10 minutes (`TIMEOUT_MS`) with no control except force-stop or the volume key. If the app is not open, the phone just rings from nothing.
- Fix: in `AlarmRingService.begin` check `NotificationManagerCompat.areNotificationsEnabled()`; if false (or `canUseFullScreenIntent()` false) call `startActivity(AlarmRingActivity)` directly from the service (allowed from a foreground service started by an alarm broadcast on API 31-36 via `AlarmManager.setAlarmClock` exemption; otherwise fall back to overlay of MainActivity with a ring banner). Also make `MainActivity` show a "Alarm ringing, Stop / Snooze" banner whenever `ringing != null`. Onboarding should state the consequence (P1-6).

### P0-6 No recovery path if the encrypted DB cannot be opened ("Reset", "Export")
- Where: nowhere in the code. This is the user-facing half of P0-1, listed separately because the owner requirement is "always a resolution or guide screen". There is no screen that explains, no export of the still-readable pieces (settings, backup JSON in Drive), no "Reset app data" with confirmation. Related: after any reset the user must be able to restore from Drive (P0-3, P0-4).
- Fix: see design proposal.

## P1 (clear UX/visual defect or inconsistency)

### P1-1 "I already use Cove" is a dead end
- Where: `feature/onboarding/OnboardingScreens.kt:78` (`nav.go(Routes.Connect)`), `ConnectScreen`.
- Repro: fresh data, Welcome > I already use Cove.
- Actual: Connect services opens with only a back arrow. There is no "Continue" or "Restore my data" action; `onboarded` is never set, so back returns to Welcome and a process restart shows Welcome again. After connecting the user must still run the three steps, and "Continue" on the wake step overwrites the synced Wake alarm with the default 6:30 (`saveWakeTime` updates the existing `kind == "wake"` row).
- Fix: after a successful sign-in or setup-code paste show a bottom action "Continue" that calls `OnboardingViewModel.finish` and offers Restore; do not call `saveWakeTime` when a wake alarm already exists.

### P1-2 Settings Privacy copy is inaccurate
- Where: `feature/me/MeSheets.kt:143` ("Your voice").
- Text says a short voice command "may be sent to be transcribed by Google's Gemini through your own database". Per `docs/AI.md` (lines 31-39): Gemini never transcribes (cloud speech is deliberately not built); it parses an already-transcribed text of at most 160 characters; with a Gemini key the call goes straight from the phone to Google with the user's own key, the Edge Function ("your own database") is legacy; and the Android recognizer counts as Cloud when the phone has no on-device model, so audio can reach Google's servers. Brief composition and Review categorisation also send non-journal text to Gemini and are not mentioned.
- Fix: "Speech is turned into text by your phone. On phones without an offline speech model, Android may send the audio to Google to do this. If you add a Gemini key, short non-journal commands, brief facts and expense notes (amounts removed) are sent to Google with your own key. Journal entries, photos and voice notes never are."

### P1-3 Test connection result is below the fold
- Where: `feature/connect/ConnectSheets.kt` (Supabase sheet, same layout in Gemini/Google).
- Repro: Connect > Supabase > fill values > Test connection (sheet is 2143 px of 2532 and scrolls).
- Actual: the buttons are clipped at the sheet's bottom edge ("Save"/"Test connection" half cut off in the screenshot) and the verdict appears under them (`Cannot reach Supabase...` at y=2316-2436 after scrolling). The user taps and sees nothing change.
- Fix: put the verdict directly above the buttons, scroll to it (`BringIntoViewRequester`) and add bottom padding for the system bar.

### P1-4 Text-size selector breaks words
- Where: `feature/me/MeSheets.kt` Look sheet, `design/components/Controls.kt` `Segmented`.
- Repro: Me > Look and text size at default font scale.
- Actual: "Default" and "Largest" wrap as "Defaul/t" and "Larges/t" in 2 lines while "Small"/"Large" are 1 line (screenshot).
- Fix: `maxLines = 1` with `softWrap = false` and reduce horizontal padding, or use "Normal"/"Big" labels.

### P1-5 Raw exception text reaches the UI
- Where: `feature/me/MeLogic.kt:88` (`"Something went wrong: ${result.reason}"`), `BackupService.guarded` (`e.message ?: ...`).
- Actual: the Room message in P0-3 is shown verbatim; any SQL/IO/JSON parse message from a bad backup file would be too.
- Fix: map `Failed` to "Backup did not finish. Try again; if it keeps happening, send the crash note from Me > Help." and keep the reason in `CrashNote`.

### P1-6 Permission denial gives no consequence or guide
- Where: `OnboardingScreens.kt:130` (mic result ignored: `{ next() }`), `AlarmPermissionScreen` (Finish stays enabled after Don't allow; helper text static).
- Actual: after Don't allow the flow continues silently. The notification row only changes its button to "Settings". Nothing says "reminders and alarm screens will not appear". The helper "Turn on 'Allow setting alarms', then press back" stays visible even when the row says Allowed. Later, Today/Plan show no banner when POST_NOTIFICATIONS is off (nudges, reminders, brief-ready never post: verified `importance=NONE` in `dumpsys notification`).
- Fix: a `PermissionHealth` card on Me ("Notifications are off, so alarms cannot show a screen. Turn on") and a one-line consequence under each denied row; hide the helper when allowed.

### P1-7 Sync "Error" with no detail or next step
- Where: `feature/me/MeLogic.kt:67` (`status is SyncStatus.Failed -> "Error"`), Me > Connect services row.
- Actual: the row says "Error". There is no reason (offline, key rejected, tables missing, quota) and no action.
- Fix: carry a typed reason in `SyncStatus.Failed` and show "Sync paused: can't reach Supabase. Retry" / "Supabase rejected the key. Fix in Connect services".

### P1-8 Backup sheet gives no next step when not signed in
- Where: `feature/me/MeViewModel.kt:75-77`.
- Actual: "Sign in with Google (Sync) to use Drive backups." with no button; "(Sync)" is jargon. The Restore button stays enabled and does nothing new.
- Fix: replace the primary button with "Open Connect services" in that state.

### P1-9 Almost no accessibility semantics
- Where: whole app; `grep semantics|Role|contentDescription` finds only `WakeWheel.kt:53`, `OnboardingParts.kt:75` and widgets.
- Actual (uiautomator): switches (`CoveSwitch`, `design/components/Controls.kt:57`) and rows are plain `View` with no role, state or label; the nested clickable (row + 46x28 switch) is announced as two unlabeled buttons; icon-only buttons (back arrow on Connect, close X on Voice, dock mic, brief collapse/play) have `content-desc=''`; the section/page headings are not marked `heading()`. TalkBack announces "double tap to activate" with no text for most of these.
- Fix: `Modifier.toggleable(role = Role.Switch)` on the row and `clearAndSetSemantics` on the switch; `Role.Button` + `contentDescription` in `pressable`/`IconButton`; `semantics { heading() }` in `CoveText` title styles.

### P1-10 Wake wheel cannot be operated by assistive tech
- Where: `feature/onboarding/WakeWheel.kt:53`. Description says "Swipe up or down to change" but there are no `ScrollBy`/`SetProgress` semantics actions. TalkBack users must use Skip, which leaves the default time. Same wheel in Me > Wake-up time sheet.
- Fix: add `scrollBy` and `setProgress` custom actions (+15/-15 min).

### P1-11 200% font scale: cramped rows, status text squeezed
- Where: Connect rows (`feature/connect/ConnectParts.kt`), Me rows (`feature/me/MeParts.kt:44` uses `heightIn(min = 52.dp)` without vertical padding), onboarding InfoCard rows.
- Repro: `settings put system font_scale 2.0`, open Connect and Me.
- Actual: "Paste setup code" wraps to three lines, "Not set" shares the line with a 56 px label column, wrapped second lines touch the hairline ("devices", "Keeps your space yours" sit on the divider), headline and body touch on Connect and Welcome (no gap), welcome headline overlaps the glow. Everything is still reachable and no truncation, which is good.
- Fix: add `padding(vertical = 12.dp)` inside rows and let the value column go under the label (`FlowRow`/`Column`) when `fontScale > 1.3`.

### P1-12 Brief sheet "Calendar" and permission sheets: stale state after permanent denial
- Where: `feature/me/BriefSettings.kt:96-130`. After the second "Don't allow" the sheet still shows "Allow calendar"; the next tap does nothing (no system dialog because the permission is USER_FIXED) and only then flips to "Open settings". One dead tap.
- Fix: compute `canAsk` from the launcher result callback (`!ok && !shouldShowRationale`) and set a saved state flag.

### P1-13 Contrast failures
- Computed from `design/Color.kt`: off-state switch track `#E9E9E6` on card `#FFFFFF` is 1.22:1 (dark 1.25:1), below 3:1 for UI components; headline tail colour `tail #8E9096` on canvas is 2.9:1 (large text needs 3:1; seen on every onboarding title and the brief headline); placeholder `#A4A6AB` on white is 2.44:1; dark `tail #6B6D73` on card 3.32:1 (ok for large only). Body `muted` is fine (4.7-6.9:1), `saved` green 5.07:1.
- Fix: off-track `#C9CACF` (light) / `#4A4B50` (dark), tail `#7D7F85`, placeholder `#8A8C92`.

### P1-14 Dock covers the last Me rows and scrolling content passes under the status bar
- Where: `MeScreen.kt` (padding bottom `DockClearance` but the first screenful ends under the dock), no scrim at the top.
- Actual: when scrolled, section titles ("More", "Photos and backup") overlap the clock text (`4:39More`, screenshot). Hit test is fine.
- Fix: draw a canvas-coloured gradient scrim of `coveTopInset()` height above scrolling content.

### P1-15 Lock screen: "Turn off app lock" needs no authentication
- Where: `feature/security/LockScreen.kt` (`AuthAvailability.None` branch), `Authenticator.availability`.
- `None` is also returned for transient `BIOMETRIC_ERROR_HW_UNAVAILABLE`/`SECURITY_UPDATE_REQUIRED`/`STATUS_UNKNOWN`, so a person holding the phone during such a state can disable the lock in one tap. Verified on emulator (no screen lock): button visible, tap goes straight into the app.
- Fix: show "Turn off app lock" only when `canAuthenticate` returns `BIOMETRIC_ERROR_NONE_ENROLLED` for both authenticators and `KeyguardManager.isDeviceSecure` is false; otherwise show "Try again".

### P1-16 Silent loss of saved credentials
- Where: `data/config/CredentialStore.kt` `read()` (`getOrElse { file.delete(); Credentials() }`). A corrupt or undecryptable credentials file is deleted and the Connect screen just says "Not set" for every service, with no explanation, so sync, Drive and Gemini quietly stop. Verified with a random file (no crash, graceful, but silent).
- Fix: keep the file until the user dismisses a one-time notice "Your saved service details could not be read. Paste your setup code again" and remember the flag in prefs.

## P2 (polish)

- P2-1 Welcome headline wraps with an orphan: "Say it once. Cove / remembers / the rest." (`OnboardingScreens.kt:57`, `BalancedText`); expected two balanced lines.
- P2-2 Me and Connect cards sit 20 dp from the screen edge (60 px) while headings and onboarding use 24 dp (72 px); CONTRIBUTING-style spec says 24 dp.
- P2-3 "Wake-up time" and "Habits" live under a catch-all "Also" group at the bottom of Me; wake time belongs in "Day".
- P2-4 Onboarding "Allow" pill is 44 dp high (`PillButton(height = 44.dp)`), the enclosing tap region is 48 dp so it passes; make the visible pill 48.
- P2-5 Permission rows: "Bundled, three times a day" is unclear; say "Your daily summaries and reminders".
- P2-6 `startActivity(Permissions.exactAlarmSettings/notificationSettings/appSettings)` is unguarded (`OnboardingScreens.kt:187-194`, `BriefSettings.kt:125`); OEM builds without the intent crash with `ActivityNotFoundException`.
- P2-7 `AlarmScheduler.register` catches `SecurityException` and silently falls back to inexact `setAndAllowWhileIdle` (`AlarmScheduler.kt:56`): the alarm may ring late with no guide.
- P2-8 `UpdatingSplash` is a blank canvas for any non-migrating wait; no timeout and no hint if `prepare()` stalls.
- P2-9 Brief player opens with "Now reading · 1 of 0", 0:00/0:00 for a moment before loading; and its play button is an ellipse (about 61x76 dp) rather than a circle (`feature/brief`).
- P2-10 Setup code success has no visible confirmation line on Connect (sheet closes, rows change to "Saved"); `describe()` result appears unused.
- P2-11 "Spoken replies" defaults to On on a fresh install, but the Privacy/onboarding copy never mentions it; first reply may be spoken unexpectedly in public.
- P2-12 Jargon in service errors: "OAuth client", "SHA-1", "consent screen is In production", "anon key", "PGRST" is not shown (good). Acceptable for a setup flow but add a "Show details" fold.
- P2-13 Header says "Your data lives in your own space" before anything is connected (true locally, but "in your own space" is vague); the Welcome promise is the same.
- P2-14 Nudge/reminder small icon renders as a filled dot (`ic_notification`) and looks like a placeholder.
- P2-15 `rememberSaveable` sheet state is lost on process death for the Connect sheets (`--es sheet` is debug only); the user returns to the list. Minor.
- P2-16 Widget Tasks rows are 40 dp high (below 48 dp); toggle latency on first tap after the process was killed was about 5 s (cold process) with no visual feedback.
- P2-17 Alarms row on Me lists only enabled alarms ("6:30 am · 3 more"); disabled alarms are invisible in the summary.

## Verified working

- Onboarding forward path, Skip on every step, Back on every step (Wake -> Welcome -> exits), mic Allow / Don't allow (continues; Voice screen then shows "Voice is off. Typing works just as well." with an "Allow microphone in settings" link), notifications Don't allow -> "Settings" shortcut, Finish and "Use reminders only", process death on step 2 (restores to step 2 via `am kill` + recents).
- All Me rows and sheets open and persist: wake time, brief on/off, city sheet, calendar sheet (ask -> deny -> deny -> open settings), nudges, one-thing, spoken replies, reduce motion, look/text size, privacy, photo quality, Wi-Fi switch, backup/restore sheets (UI only), app lock toggle, Connect services with four sheets, Paste setup code (empty -> "Paste a setup code first."; valid code fills Supabase and Gemini; sheet sits above the keyboard).
- Supabase unreachable gives "Cannot reach Supabase. Check your internet connection and the project URL." within the 10 s timeout; other service verdicts reviewed in `ConnectionTests.kt`, `ConnectLogic.kt`, `GeminiConnectionCheck.kt`: all plain language with a next step, no keys or stack traces.
- Corrupt credentials file and corrupt session store: no crash (see P1-16).
- Alarm rings (service, tone, vibration) with notifications revoked and with the app in foreground: no crash (but see P0-5).
- Reminder notification shows Done and Snooze 10 min; Done ticks the to-do (`NudgeReceiver`), brief-ready notification opens the Brief player. Tasks widget renders live data, taps toggle a to-do and redraw; pin dialog works for all four widgets via `--es pin_widget`.
- Lock screen: cold start is locked, UI graph not composed while locked, with no screen lock the screen offers "Open security settings" and "Turn off app lock". Credential change cannot invalidate the DB key (no `setUserAuthenticationRequired`).
- Dark parity of Me/Connect good; touch targets (uiautomator): rows 156 px (52 dp), segmented controls 144 px (48 dp), switches 144 px, dock tabs 174x180 px, close/back 144 px.
- Stability: `monkey -s 7/11/23 --pct-syskeys 0 -v 1500` no crash or ANR; no StrictMode violations from app code (one framework `LeakedClosableViolation` from `InsetsSourceControl`); `am start -W` TotalTime 424-486 ms.

## Could not test

- Full storage (`ENOSPC`) during migration/backup/photo save; kill during the plaintext->encrypted migration (covered by design in `PlaintextMigrator`, only the corrupt-input case was run); a corrupt single row (needs the key to write); real biometrics (lockout, enrolment removed, device credential change); voice tile / shortcut while locked beyond reading `AppLock`; real Google sign-in, Drive consent denied and Gemini quota (verified messages by reading code only); exact-alarm revocation (both permissions declared, `USE_EXACT_ALARM` cannot be revoked); full-screen-intent revocation; usage-stats revocation (code returns null safely); widgets with a corrupt DB (inferred from P0-1) and VoiceWidget/Spent/Next live behaviour (only Tasks was added to the home screen); TalkBack traversal (semantics inferred from the uiautomator tree).

## Resilience design proposal

Goal: a user can never be stuck in a crash state and always sees either the app, a recovery screen or a guide. Minimal, no new dependencies, about 5 small classes.

### 1. `CrashNote` and global handler (all processes)
`core/CrashNote.kt`:
```kotlin
object CrashNote {
    fun install(app: Application)          // Thread.setDefaultUncaughtExceptionHandler, chains to the previous one
    fun last(context: Context): Note?      // file noBackupFilesDir/crash.txt: time, thread, class name, top 6 frames (no message, no user data)
    fun record(context: Context, where: String, t: Throwable)
    fun consecutiveFailures(context: Context): Int  // counter in prefs, reset after the UI shown 10 s
}
```
Called first thing in `CoveApp.onCreate` (before `AppContainer`). The handler writes the note and increments `startFailures`, then calls the old handler. `MainActivity` clears the counter after `reportFullyDrawn` plus 10 s.

### 2. `DatabaseGuard` instead of crash-on-open
In `AppContainer.prepareDatabase()` return a sealed `DbState { Ready, Failed(kind) }` where `kind` is `Corrupt`, `KeyMissingOrInvalid` or `MigrationFailed`, found by wrapping `dbFactory.prepare()` and a `database.openHelper.writableDatabase` call in try/catch (`SQLiteNotADatabaseException`, `GeneralSecurityException`, `IOException`). Also wrap `startServices()` in `runCatching` and give `appScope` a `CoroutineExceptionHandler` that records and never rethrows. Do not auto-generate a new key when `cove.db` exists but `cove.key` does not: report `KeyMissingOrInvalid` instead (`DatabasePassphrase.get(allowCreate = !dbFile.exists())`).

### 3. `RecoveryScreen` (rendered by `MainActivity` when `DbState.Failed`, or when `CrashNote.consecutiveFailures >= 2`)
It uses only static resources and `CoveTheme`, never `container.database`. Copy and actions per failure class:

| Class | Title / body | Actions |
|---|---|---|
| Corrupt DB | "Cove couldn't open your data" / "The file on this phone looks damaged. Nothing was deleted yet." | **Try again** (reopen), **Save a copy of the file** (share `cove.db` + `cove.key` via FileProvider so support or a later fix can recover it), **Restore from Google Drive** (sign-in then `restoreLatest` into a fresh DB), **Start fresh** |
| Key missing/invalid | "Cove lost the key to your data" / "This can happen after a phone transfer or security reset. Your data in Google Drive and Supabase is safe." | **Restore from Google Drive**, **Start fresh** |
| Migration failed | "Cove couldn't finish updating" / "Your old data is untouched." | **Try again**, **Save a copy**, **Start fresh** |
| Crash loop (2+ crashes within a minute, DB fine) | "Cove had trouble starting" / "Last time: <class name, time>." | **Open in safe mode** (skip widgets, nudge scheduler, brief, sync for this launch), **Copy crash note**, **Start fresh** |

"Start fresh" (and "Reset app data" in Me > Help) is a two-step confirm: "This deletes everything on this phone: to-dos, alarms, money, journal and photos kept only here. Anything backed up to Google Drive or synced to Supabase is not touched. Delete" with the button disabled for 3 s. It deletes `databases/cove.db*`, `no_backup/cove.key`, credentials and session, then restarts the process via `ProcessPhoenix`-style `startActivity` + `killProcess`. After reset onboarding shows with "I already use Cove" leading to restore (needs P0-4).

### 4. Safe mode
`SafeMode` flag in prefs consumed by `CoveApp.startServices()`: when set it skips `AlarmRescheduler`, `NudgeScheduler`, `WidgetUpdater`, `startCloud` and sets a banner on Today: "Safe mode: sync, widgets and reminders are paused. Restart Cove to turn them back on." Cleared when the user taps Restart.

### 5. Background components
Wrap every receiver/worker body (`AlarmReceiver`, `BootReceiver`, `NudgeReceiver`, `NudgeBootReceiver`, `WidgetRefreshWorker`, `BriefWorker`, `SyncWorker`, `MonthlyBackupWorker`, `MediaUploadWorker`) in `runCatching { }.onFailure { CrashNote.record(...) }`; workers return `Result.retry()` at most 3 times then `Result.failure()`. Alarm fallback ring (P0-2) is the one case that must keep working: schedule time, label and snooze minutes go into the PendingIntent extras so `AlarmReceiver` can ring without a DB. If the DB is unavailable post a notification (or full-screen intent) "Cove couldn't open your data, but your alarm is ringing. Open Cove to fix".

### 6. Service failures (already good, small gaps)
- Every `TestResult`/sync/backup message gets an optional action: "Open Connect services", "Retry", "Open settings". Replace "Error" (P1-7) and "Something went wrong: <reason>" (P1-5) with typed reasons.
- Add a 15 s timeout to Google sign-in (`GoogleSignIn.attempt`), `drive.connect()` and `testCloud` so `busy` can never spin forever (the `finally` already clears it).
- Offline: keep `OfflineNotice`; add "Retry now" to the sync row.

### 7. Permission health (one card on Me, one banner on Today when something critical is off)
`PermissionHealth.check(context)` returns the list of missing: notifications (alarms cannot show a screen), exact alarms, full-screen intent, mic, calendar. Copy: "Alarms may not show a screen: Notifications are off. [Turn on]". Same card replaces the static onboarding helper text.

### 8. Lock edge cases
`LockScreen` on `AuthAvailability.None` shows "Open security settings" and "Try again"; only offers "Turn off app lock" when the device truly has no screen lock (P1-15). Add a visible "Locked out? Use device PIN" after `ERROR_LOCKOUT`.

### 9. Verification plan (all scriptable with the same `run-as` tricks)
1. Corrupt DB, delete key, garbage key, corrupt plaintext: expect Recovery screen, no crash, `adb logcat -b crash` empty.
2. Alarm with corrupt DB: rings with generic label.
3. Alarm with notifications revoked: ring activity appears.
4. Fake-Drive back up and restore from Main thread via UI: "Backed up." / "Restored N items" (add a UI test).
5. `monkey` with the Recovery screen forced (`--ez forceRecovery true`).
