# Google Drive setup

Moved into the single setup guide: see "Google Drive" and "Google sign-in" in [`docs/SETUP.md`](SETUP.md). The Connect services > Google Drive sheet shows this build's package name and SHA-1/SHA-256 (with copy buttons) for the Android OAuth client, and its **Connect Drive** button runs Google's consent screen.

Debug without credentials: `adb shell am start -n app.cove.companion/.MainActivity --ez seed true --ez fakeDrive true --ez driveRun true` uses a folder-backed fake Drive under `files/drive-fake/`.
