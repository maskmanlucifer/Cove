# Google Drive setup (owner steps)

Cove stores photos, voice notes and monthly backups in a visible `Cove` folder in your Drive (scope `drive.file`, non-sensitive, no app verification). Without the steps below the app still works: uploads stay `pending`.

1. Google Cloud Console: use the same project as sign-in (see `supabase/README.md`). Enable the **Google Drive API**.
2. OAuth consent screen: add scope `https://www.googleapis.com/auth/drive.file`; set publishing status to **In production** (Testing mode expires tokens after 7 days). No verification is needed for this scope.
3. Create an **Android** OAuth client: package `app.cove.companion` plus the SHA-1 of your debug and release keystores (`./gradlew signingReport`). `AuthorizationClient` identifies the app by package + SHA-1, so no secret goes in the app.
4. Make sure `googleWebClientId` is set in `~/.gradle/gradle.properties` (Drive is switched on only when it is non-blank and you are signed in).
5. Run `supabase/migrations/0002_settings_media.sql` (adds `photo_quality`, `upload_on_wifi_only` to `settings`). The `thumbs` bucket from `0001` already exists.
6. First upload after sign-in shows Google's consent screen once; approve it. Files appear under Drive > Cove > Photos / Voice / Backups.

Debug without credentials: `adb shell am start -n app.cove.companion/.MainActivity --ez seed true --ez fakeDrive true --ez driveRun true` uses a folder-backed fake Drive under `files/drive-fake/`.
