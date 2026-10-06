# Setting up your own services (about 15 minutes)

Cove runs fully on its own with nothing configured: alarms, to-dos, habits, money, journal and the morning brief all work offline. Every service below is optional, and you bring your own accounts. **No keys ship with the app.** You paste your values into **Me > Connect services**; they are stored encrypted (Android Keystore) on this phone only, never in the app build, never in a backup, never sent anywhere except to the service they belong to.

| Service | What it gives you | Needs |
|---|---|---|
| Supabase | Sync between devices | Free Supabase project |
| Google sign-in | Proves the data is yours | Google Cloud project (free) and Supabase |
| Google Drive | Photos, voice notes, monthly backup | Google Cloud project, sign-in |
| Gemini | Trickier voice commands, brief wording | Google AI Studio API key |
| Weather (Open-Meteo) | Brief weather | Nothing |

Shortcut: once you have the values, `tools/make-setup-code.py` turns them into one `cove-setup:1:...` line. In the app tap **Connect services > Paste setup code** to fill everything at once. Treat the code like a password.

Each service sheet in the app has the same steps with "Open dashboard" buttons and a **Test connection** button that tells you in plain words what is wrong.

## 1. Supabase (sync)
1. Create a free project at [supabase.com/dashboard](https://supabase.com/dashboard).
2. Open **SQL Editor > New query**, paste the contents of `supabase/setup.sql` (in the app: Supabase sheet > **Copy setup SQL**), and run it. It is safe to run twice.
3. Open **Project Settings > API**. Copy the **Project URL** (`https://xxxx.supabase.co`) and the **anon public** key (a long text starting `eyJ`). Never use the `service_role` key; the app refuses it.
4. In the app: Connect services > Supabase, paste both, **Test connection**. A good result says it is reachable, the key works and the tables are there. If it says the tables are missing, step 2 was not run.

Free projects pause after a week without use. Using the app counts; see "Keep the project awake" in `supabase/README.md` if you want a ping.

## 2. Google sign-in
Sign-in goes Google account > your Supabase project, so do Supabase first.
1. [Google Cloud console](https://console.cloud.google.com): create a project (or reuse one).
2. **APIs & Services > OAuth consent screen**: fill in app name and your email, add yourself, then **Publish app** so the status is **In production**. (In "Testing" Google logs you out every 7 days.) No verification is needed for the basic scopes used here.
3. **Credentials > Create credentials > OAuth client ID**, type **Web application**. Copy the **Client ID** (ends `.apps.googleusercontent.com`) and the **Client secret**.
4. **Android client** (needed for the sign-in sheet to appear): create another OAuth client, type **Android**, package name `app.cove.companion`, and the SHA-1 shown in Connect services > Google Drive (the sheet reads it from the installed build). Add one per build you use (debug and release differ).
5. In Supabase: **Authentication > Providers > Google**: enable, paste the web Client ID and secret, save. Under Authentication > URL configuration nothing is needed for an Android app.
6. In the app: Connect services > Google sign-in, paste the **web** Client ID, tap **Sign in**.

## 3. Google Drive (optional)
Files are saved in a visible `Cove` folder in your Drive (scope `drive.file`: Cove can only touch files it created).
1. In the same Google Cloud project open the [Drive API page](https://console.cloud.google.com/apis/library/drive.googleapis.com) and click **Enable**.
2. Make sure the Android OAuth client from step 2.4 exists with this build's package name and SHA-1. There is no client ID to paste: Google matches the app by package name and signature.
3. Sign in first, then Connect services > Google Drive > **Connect Drive** and approve Google's screen.

## 4. Gemini
1. Open [Google AI Studio API keys](https://aistudio.google.com/apikey), create a key.
2. In Cloud billing set a **budget alert** (and, if you like, a daily quota) so a leaked key cannot cost much. Usage here is a few tiny requests a day.
3. Connect services > Gemini: paste the key (starts `AIza`), **Test connection**. Under Advanced you may change the model (default `gemini-2.5-flash-lite`) and the fallback (`gemini-2.5-flash`).

The phone calls Gemini directly with your key. Only short, non-journal text is sent (a spoken command, or a few facts such as the weather); journal content and transcripts over 600 characters never leave the device.

## What works with nothing configured
Everything local: alarms, nudges, to-dos, habits, money, journal with photos and voice notes, widgets, the rule-based voice assistant (and Gemini Nano on a Pixel 10), the app lock, and the brief from templates. Not available: sync between devices, Drive upload and backup (photos stay pending on the phone), and Gemini's cloud understanding.

## Security
Pasted values are encrypted with a non-exportable Android Keystore key and stored in the app's `no_backup` folder (`cove-credentials.bin`). They are not part of Android backups and cannot be read off the phone. Test results and logs never contain keys. If you lose the phone, rotate the Gemini key and the Supabase anon key and sign the device out in Google account security. Details of database protection: `docs/SECURITY.md`.

## Advanced: server-side key (optional, legacy)
Instead of keeping the Gemini key on the phone you can deploy the Edge Function in `supabase/functions/ai-gateway`:
```
supabase functions deploy ai-gateway
supabase secrets set GEMINI_API_KEY=... GEMINI_MODEL=gemini-2.5-flash-lite \
  GEMINI_RETRY_MODEL=gemini-2.5-flash ALLOWED_USER_ID=<your user id from Authentication > Users>
```
Leave the Gemini field in the app empty. When Supabase is configured and no Gemini key is set, the app uses the function (signed-in users only). A Gemini key in the app always wins. Tests: `deno test supabase/functions/ai-gateway`.

## Developer defaults
Debug builds may still read `supabaseUrl`, `supabaseAnonKey`, `googleWebClientId` from `~/.gradle/gradle.properties`. They only fill fields left blank in the app; values pasted in the app win.
