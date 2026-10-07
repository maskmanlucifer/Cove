# Debug extras and tools

For developers working on Cove. Nothing here is needed to build or use the app.

## Debug extras (debug builds only)
Send with `adb shell am start -n app.cove.companion/.MainActivity <extras>`.

| Extra | Effect |
|---|---|
| `--ez seed true` (`--ez dark true`, `--ez evening true`, `--ez moneyLogged true`, `--es plan todos\|empty\|drag`, `--ez conflict true`) | Load the design's sample data (`CLEAR=1 tools/run.sh` wipes data first) |
| `--es now HH:mm` (`--es date yyyy-MM-dd`) | Freeze the clock |
| `--ez journalBlocks true` | Add Journal entries `blocks-text|mid|start|mixed|legacy|missing|long` (every block layout); open with `--es route journal/blocks-mid` |
| `--es captureMode off\|ask\|auto` (`--ez catchUp true`) | Set "Payments from messages" without the UI; run the since-last-import scan now. Test with `adb emu sms send VM-PLUXEE "Rs 8 spent from Pluxee wallet"` after granting RECEIVE_SMS, READ_SMS, POST_NOTIFICATIONS |
| `--ei journalPhotos N` | Create journal entry `debug-photos` with N generated photos (several shapes, EXIF-rotated, corrupt) and a voice row; open it with `--es route journal/debug-photos` |
| `--es route <route>` | Start on a route (`alarms`, `money/categories`, `sync/conflict`, `brief`, `one-thing`, ...; see `navigation/Routes.kt`) |
| `--es tab plan --es segment 1 --es sheet categories --es title Dentist` | Open a Plan tab view, segment, sheet or prefilled title |
| `--es suggest late-night` | Fake a 1:40 am phone use for the late-night suggestion |
| `--es briefAt 51/124` | Freeze the brief player at elapsed/total seconds |
| `--es setupCode cove-setup:1:...`, `--ez forgetCredentials true`, `--es sheet supabase\|google\|drive\|gemini\|code` (with `--es route connect`) | Fill or wipe stored credentials; open a Connect services sheet |
| `--ez offline true` | Force the offline look |
| `--es voiceState listening\|result\|partial\|saved\|micoff --es transcript "..." --ei voiceSeconds N` | Open the Voice screen in that state |
| `--es voiceFail permission\|busy\|noservice\|network\|silence\|noactivity\|failover\|listen\|off` | Replace speech engines by scripted fakes to show each Voice guidance screen (`docs/VOICE_DEBUG.md`) |
| `--ez noTraining true` | Training: seed no workout plan, to see the empty page (see `docs/TRAINING.md`) |
| `--ez fakeDrive true --ez driveRun true` | Folder-backed fake Drive with a pending photo; `driveRun` uploads and backs up |
| `--ei alarm_in_min N` | Add a one-time test alarm N minutes from now |
| `--ez alarm_preview true --ei minutes M` | Open the ring screen without the service |
| `--es pin_widget next\|voice\|spent\|tasks` | Ask the launcher to pin that widget |
| `--ei nudge_in_sec N`, `--ei reminder_in_sec N`, `--ez brief_ready true` | Schedule a bundled summary / test reminder, post the brief-ready notification |
| `--ez appLock true\|false`, `--ez lockNow true` | Turn the app lock on/off, lock immediately |
| `--ez plainDb true` | Rewrite the DB as plaintext and kill the process, to exercise the migration on next launch |
| `--ez screenshots true\|false` | Allow/disallow screenshots while the lock hides them |
| `--es crashTest db-corrupt\|key-missing\|key-invalid\|migration\|storage\|unknown\|loop` | Show the Recovery screen for that failure class without damaging anything (`crash` throws on the main thread to exercise the crash handler) |

Release builds ignore all of these. Debug builds also log StrictMode violations (`adb logcat -s StrictMode`).

## Tools
- `tools/run.sh [--seed] [--dark] [--evening] [--now HH:mm]` builds, installs and launches (`CLEAR=1` clears data first).
- `tools/shot.sh out.png` screenshots the emulator.
- `tools/compare.py <NN_Name> shot.png` writes a design | device | diff image and prints the mean difference against `design/ref3x`.
