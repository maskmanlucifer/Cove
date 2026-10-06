# Cove security

## What is protected

- **Database at rest.** `cove.db` is encrypted with SQLCipher (AES-256). Fresh installs create it encrypted; installs that still hold a plaintext `cove.db` are migrated on first launch (`security/PlaintextMigrator.kt`): export into `cove.db.enc`, compare row counts of every table, atomically replace the plaintext file, delete `-wal`/`-shm`/`-journal`. A crash at any step is safe; the plaintext file is replaced only after verification, and a leftover temp file is discarded on the next run.
- **Passphrase.** 256 random bits (hex-encoded), generated once and stored in `no_backup/cove.key`, sealed by a non-exportable Android Keystore AES-256-GCM key (`SecretBox`, alias `cove_db_key`). The file is useless off this device and is excluded from backups (`allowBackup=false`, `noBackupFilesDir`).
- **UI lock.** Optional app lock (Me > Security) puts a lock screen in front of the app using `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. While locked the navigation graph is not composed at all, so no content is drawn or retained underneath. `FLAG_SECURE` hides the window from recents and screenshots while locked, and, with "Hide in recents" (default on when the lock is on), also while unlocked.
- **Relock.** After "Lock after" in the background (immediately, 1 minute, 5 minutes; default 1 minute), measured with the monotonic clock. A cold start is always locked. The tile and launcher shortcut open `MainActivity`, so they land on the lock screen; the request to start listening is kept and runs once, after unlocking.

- **Service credentials.** Supabase URL/anon key, Google web client ID and the Gemini key are pasted in the app and stored as one Keystore-sealed file in `no_backup/` (`SecretBox`, alias `cove_credentials`); not baked into the app, and never in backups or logs.

## The trade-off (read this)

The product requires that alarms ring, notifications post and widgets render while the app is locked, and that WorkManager (sync, brief, backups) keeps working in the background. Those components read the database without any user present. Therefore **the biometric lock gates the UI, not the database key**: the Keystore key is created without `setUserAuthenticationRequired`, so any code running inside the app's own process can open the database at any time.

What this means:

- It defeats offline attacks: copying `cove.db` (adb backup of a debuggable build, a stolen disk image, a root-less file leak) yields ciphertext, and the key cannot leave the secure hardware.
- It does **not** defeat someone who holds an unlocked phone and can launch the app: the app lock stops them, but the data is not cryptographically bound to the fingerprint. A rooted attacker who can run code as the app, or inject into its process, could read the database without authenticating.
- PLAN.md section 7 described a per-use authenticated key that makes the database unreadable until the biometric passes. That design cannot coexist with background alarms and widgets, so it was deliberately not built. If a stricter mode is ever wanted, it would need a second, authenticated key wrapping a separate "private" partition (for example journal entries only) while alarms and todos stay on the background key.

Other limits:

- Relocking drops the navigation stack and any unsaved screen state (the user returns to Today after unlocking).
- Deleted plaintext cannot be wiped from flash; the migration removes the file, not its physical blocks.
- The alarm ring screen is never gated (`AlarmRingActivity` shows over the lock screen and reads through `AppContainer`).
- If the device has no screen lock any more, the lock screen offers "Open security settings" and "Turn off app lock" so the user is never locked out.

## Debug hooks (debug builds only)

`--ez appLock true|false`, `--ez lockNow true`, `--ez screenshots true` (drops `FLAG_SECURE` so `adb screencap` works), `--ez plainDb true` (rewrites the DB as plaintext and kills the process so the next launch exercises the migration).
