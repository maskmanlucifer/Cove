# Data controls

## Clear all data (Me > Clear all data, also in the Privacy sheet)

One option. It returns Cove to a factory-new state on this phone, exactly like a fresh install.

The confirm sheet lists what will be deleted (counts per area from the database, number and size of photos and voice notes, database size), says it cannot be undone, offers a skippable **Save a backup first** (to Google Drive when connected; otherwise a readable `cove-backup-YYYY-MM.json.gz` file of the entries, without photos), offers **Save my setup code first** and has **Cancel** (default focus) and **Hold to clear all data** (hold one second; a tap does nothing; screen readers get a long-press action).

### What is deleted on the phone
Encrypted database, journal photos, voice notes and thumbnails, DataStore files, cache, local recovery copies, search index, categorisation memory, the import log and pending payments from messages (`sms_import_log`, `sms_pending`: parsed fields and hashes only, never message text), the "Payments from messages" mode, briefs, undo state, crash notes, all SharedPreferences (settings, sign-in session, Drive consent), stored credentials (Supabase, Google, Gemini), the database key file and every Android Keystore key. Alarms (including snoozes), reminders, nudges, the rest timer, WorkManager jobs and notifications are cancelled first. Widgets redraw empty on the next start. Only WorkManager's own job database and preferences stay (no user data).

### Order (cannot leave a half-initialised app)
1. (Optional) cloud deletion, see below.
2. Stop background work, cancel alarms/jobs/notifications.
3. Write the `pending_wipe.json` marker (app data root, outside every wiped folder), close the database, restart the app in a new process.
4. `CoveApp.onCreate` (main process only) sees the marker and runs `WipeProcessor` before anything can open the database: database files first, then files, `no_backup`, cache, preferences, Keystore keys. The marker is deleted last. A kill at any point is repaired by the next start; a file that cannot be deleted is retried three times, then the marker stays and the next start retries (the database is already gone, so the app starts fresh).
5. The first launch shows "Your data on this phone was cleared." once.

Recovery's **Start fresh** uses the same machinery (`DeviceWipe`), so it is also a full fresh-install reset including connections.

### Also delete my cloud copies (optional)
Only shown when signed in. Unchecked by default. Checking it requires typing `DELETE`. It runs before the local clear and reports exactly what happened, for example `Supabase: 1,240 rows. Drive: 38 files. Thumbnails: 38.`:
- Supabase: `DELETE /rest/v1/<table>?<pk>=not.is.null` for every table in `SyncTables`, 500 rows per request (`limit` + `order`), under row-level security so only your rows are touched. 429/5xx and network errors are retried (1 s, 2 s); a 401 refreshes the token once; a missing table counts as empty.
- Thumbnails: `thumbs/<user id>/*` through the Storage list and delete API.
- Drive: the files in the Cove folders that the app created (`drive.file` scope), then the folders.
A failed step is shown as failed with what was deleted so far; the sheet then offers Try again, Clear this phone anyway, or Cancel. Nothing is reported as deleted unless the server confirmed it.

## Updates keep your connections
Credentials are one Keystore-sealed file in `no_backup/` (alias `cove_credentials`), the session is Keystore-sealed in `shared_prefs/cove_session.xml`, Drive consent is a flag in `shared_prefs/cove_drive.xml`. Nothing in the app clears them except the two explicit actions above. Audit results: no `Application.onCreate` migration touches them; database migrations never touch these files; an unreadable credentials file is no longer deleted (a Keystore hiccup used to drop the connections silently); Recovery's Restore moves only the database and its key. Verified on the emulator: setting credentials with the `--es setupCode` debug extra, then `./gradlew :app:installDebug` over the install and a process kill leave `cove-credentials.bin` byte-identical and Connect services showing Saved.

Not preserved by Android, so use the setup code (Connect services > My setup code; see `docs/SETUP.md`): uninstall, a different signing key (debug vs release), a new phone.
