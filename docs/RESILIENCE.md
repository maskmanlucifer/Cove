# Resilience: a user is never stuck

Rule: the user never sits in a crash loop and always has either the app, a Recovery screen or a guide with one next step. Code lives in `resilience/`, `feature/recovery/` and `feature/permissions/`.

## Layers

1. **Crash handler** (`CrashHandler`, installed first in `CoveApp.onCreate`). Writes a `CrashNote` (time, thread, exception class, top frames, app version; never the message, never user data) to `no_backup/crash-notes.txt`, then calls the previous handler. Two exceptions: a database failure on a background thread (for example inside Room's own coroutines) is noted and survived, because startup already routes the user to Recovery and alarms ring from their mirror.
2. **Crash loop.** Two fatal crashes within 60 s (`CrashLoop`) mean the next launch is a safe-mode launch: the Recovery screen opens (reason "Cove had trouble starting") and no background service starts. "Try again" clears the notes and restarts.
3. **`DatabaseGuard`** runs once at startup off the main thread (`AppContainer.prepareDatabase`): key, one-time encryption upgrade, open, `SELECT` on `sqlite_master`. It never throws; it returns a `DbCheck` and the app shows the app or Recovery. A new key is only created when no encrypted database exists (`EncryptedDatabase.mayCreateKey`); otherwise the missing key is reported, because a new key would make the old data unreadable for ever. Cove never deletes data by itself.
4. **Background components** (`AlarmReceiver`, `BootReceiver`, `NudgeReceiver`, widgets, every worker) catch failures, note them and finish; receivers wait at most 8 s on the database.
5. **Alarms without the database.** `AlarmScheduler.sync` mirrors enabled alarms (time, days, label, kind, sound, snooze) to `no_backup/alarm-mirror.json`, plain on purpose. If the database cannot be read, the receiver rings from the mirror (or a plain "Alarm"), the boot receiver re-registers from it, and the ring screen works without data.

## Failure classes

| Class | Cause | User sees | Actions |
|---|---|---|---|
| Key missing | Key file deleted, or the Keystore key is gone (transfer, security reset) | "Cove can't unlock your data" | Restore from a backup, Save a copy (explains it stays locked), Start fresh |
| Key invalid | Key file damaged | "Cove can't unlock your data" (damaged key file) | Try again, Restore, Save a copy, Start fresh |
| Corrupt | Database file damaged | "Cove couldn't open your data" | Try again, Save a copy, Restore, Start fresh |
| Migration failed | Upgrade failed | "Cove couldn't finish updating" | Try again, Save a copy, Restore, Start fresh |
| Storage full | No space | "Your phone is out of space" | Open storage settings, Try again, Save a copy |
| Unknown | Anything else at open | "Something stopped Cove from opening" | Try again, Save a copy, Restore, Start fresh |
| Crash loop | 2 crashes in 60 s, database fine | "Cove had trouble starting" | Try again, Save a copy, Start fresh |

"Technical details" (collapsed) shows the reason and the last `CrashNote`; "Copy details" copies it. No exception names or codes are shown otherwise.

## Recovery actions (`RecoveryActions`, no database, key or network needed)

- **Try again**: clears crash notes and restarts the app through a helper activity in its own process (`RestartActivity`), so the new process never races the dying one.
- **Save a copy of my data**: system document picker (`CreateDocument`), a zip with the raw (encrypted) `cove.db` and sidecars, the technical notes and the journal photos and voice notes.
- **Restore from a backup**: pick a `cove-YYYY-MM.json.gz` (local or via Drive in the picker). The unreadable database and key are moved to `files/recovery-old/<time>/` (not deleted), the backup is staged and imported into the fresh database on the next start, before the UI opens.
- **Start fresh**: two steps. It states what is deleted, offers "Save a copy first" when none was saved, and needs the word DELETE typed. It is the same full reset as Me > Clear all data (`DeviceWipe`, see `docs/DATA_CONTROLS.md`): alarms and jobs are cancelled, a `pending_wipe` marker is written and the app restarts; the database, keys, credentials, photos and settings are deleted at the start of the next process.
- **Open storage settings** when storage is full.

## Permissions

`PermissionHealth` (pure) decides which gaps matter; `PermissionGuide` / `GuideBanner` show one calm banner with one button that opens the right Settings page. State is re-read on every resume, so revocation is noticed. Used on Alarms (notifications, exact alarms, full-screen alerts), Me, and Today when reminders exist. Onboarding explains the consequence of denied notifications and does not block.

With notifications denied Android blocks a background start of the ring screen (verified on API 36). The alarm still rings; `RingingBanner` shows Stop and Snooze at the top of the app and of the Recovery screen whenever an alarm rings, and the Alarms and Me screens explain how to turn notifications on.

## Backup and restore

Back up and Restore run on `Dispatchers.IO` with a 2-minute timeout; failures show a plain sentence, never exception text, and the cause goes to the crash note. "Empty" for restore means no user-authored data (`RestoreRule`): the settings row and the onboarding "Wake up" alarm do not count, and a restored wake alarm replaces that default.

## Verifying by hand

```
adb shell run-as app.cove.companion dd if=/dev/urandom of=databases/cove.db bs=4096 count=1   # corrupt
adb shell run-as app.cove.companion mv no_backup/cove.key files/key.bak                       # key missing
adb shell run-as app.cove.companion dd if=/dev/urandom of=no_backup/cove.key bs=92 count=1    # garbage key
adb shell am start -n app.cove.companion/.MainActivity --es crashTest loop                    # simulate a class
adb logcat -b crash -d                                                                        # must stay clean
```
