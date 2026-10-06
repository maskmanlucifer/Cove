# Cove

A calm, voice-first companion for Android: alarms, to-dos, habits, money, a journal and a morning brief, all working offline on an encrypted on-device database, with optional sync to your own Supabase project and your own Google Drive. Kotlin, Jetpack Compose, Room + SQLCipher, manual DI. Private app for one user; running cost is about zero (`PLAN.md` section 9).

## Architecture map
All code is in `app/src/main/kotlin/app/cove/companion/`:

| Package | What lives there |
|---|---|
| `CoveApp`, `AppContainer`, `MainActivity` | Application entry, dependency graph, single activity with the lock gate |
| `core/` | Freezable `Clock`, formatting, notification channels, `Permissions`, ViewModel helpers |
| `design/` | Colour tokens, Geist type scale, shapes, icons, `CoveTheme`, shared components (`CoveText`, `CoveSheet`, dock, orb) |
| `navigation/` | Routes, nav host, tab host |
| `security/` | SQLCipher open helper, Keystore-wrapped key, plaintext-to-encrypted migration |
| `data/` | Room (`local/`), repositories, sync (Supabase over Ktor), auth, Drive, media, backup, AI gateway, search |
| `feature/` | One package per screen area: onboarding, today, plan, habits, money, journal, me, alarms, voice, brief, suggest, nudges, widgets, security, sync |

More detail: `PLAN.md` section 4. Backend: `supabase/` (`setup.sql`, migrations, optional `ai-gateway` Edge Function).

## Build, run, test
Requirements: JDK 17, Android SDK 36, an emulator or device on Android 12+ (minSdk 31).
```
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # install on the connected device (export ANDROID_SERIAL=... if several)
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # lint
./gradlew :app:assembleRelease        # R8 + signing, see docs/RELEASE.md
```
No credentials are needed to build. Cloud services are optional and set up inside the app (Me > Connect services, `docs/SETUP.md`); values are stored encrypted on the phone. `~/.gradle/gradle.properties` entries (`supabaseUrl`, `supabaseAnonKey`, `googleWebClientId`) remain as debug-build defaults only. Without any of it the app is fully local.

## Debug extras (debug builds only)
Send with `adb shell am start -n app.cove.companion/.MainActivity <extras>`.

| Extra | Effect |
|---|---|
| `--ez seed true` (`--ez dark true`, `--ez evening true`, `--ez moneyLogged true`, `--es plan todos\|empty\|drag`, `--ez conflict true`) | Load the design's sample data (`CLEAR=1 tools/run.sh` wipes data first) |
| `--es now HH:mm` (`--es date yyyy-MM-dd`) | Freeze the clock |
| `--es route <route>` | Start on a route (`alarms`, `money/categories`, `sync/conflict`, `brief`, `one-thing`, ...; see `navigation/Routes.kt`) |
| `--es tab plan --es segment 1 --es sheet categories --es title Dentist` | Open a Plan tab view, segment, sheet or prefilled title |
| `--es suggest late-night` | Fake a 1:40 am phone use for the late-night suggestion |
| `--es briefAt 51/124` | Freeze the brief player at elapsed/total seconds |
| `--es setupCode cove-setup:1:...`, `--ez forgetCredentials true`, `--es sheet supabase\|google\|drive\|gemini\|code` (with `--es route connect`) | Fill or wipe stored credentials; open a Connect services sheet |
| `--ez offline true` | Force the offline look |
| `--es voiceState listening\|result\|partial\|saved\|micoff --es transcript "..." --ei voiceSeconds N` | Open the Voice screen in that state |
| `--ez fakeDrive true --ez driveRun true` | Folder-backed fake Drive with a pending photo; `driveRun` uploads and backs up |
| `--ei alarm_in_min N` | Add a one-time test alarm N minutes from now |
| `--ez alarm_preview true --ei minutes M` | Open the ring screen without the service |
| `--es pin_widget next\|voice\|spent\|tasks` | Ask the launcher to pin that widget |
| `--ei nudge_in_sec N`, `--ei reminder_in_sec N`, `--ez brief_ready true` | Schedule a bundled summary / test reminder, post the brief-ready notification |
| `--ez appLock true\|false`, `--ez lockNow true` | Turn the app lock on/off, lock immediately |
| `--ez plainDb true` | Rewrite the DB as plaintext and kill the process, to exercise the migration on next launch |
| `--ez screenshots true\|false` | Allow/disallow screenshots while the lock hides them |

Release builds ignore all of these. Debug builds also log StrictMode violations (`adb logcat -s StrictMode`).

## Tools
- `tools/run.sh [--seed] [--dark] [--evening] [--now HH:mm]` builds, installs and launches (`CLEAR=1` clears data first).
- `tools/shot.sh out.png` screenshots the emulator.
- `tools/compare.py <NN_Name> shot.png` writes a design | device | diff image and prints the mean difference against `design/ref3x`.

## Docs
- `PLAN.md`: product and architecture plan, decisions, phases.
- `docs/CONTRIBUTING.md`: how features are built and verified.
- `docs/SECURITY.md`: database encryption and app lock.
- `docs/SETUP.md`: connect your own Supabase, Google, Drive and Gemini (15 minutes); `tools/make-setup-code.py` builds a one-paste setup code.
- `docs/DRIVE_SETUP.md`: pointer to the Drive part of the setup guide.
- `docs/RELEASE.md`: signing, release builds, R8 rules, lint notes.
- `supabase/README.md`: backend files (setup.sql, migrations, legacy gateway).
