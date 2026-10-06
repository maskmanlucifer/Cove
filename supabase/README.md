# Cove backend (Supabase)

One free Supabase project holds the synced tables, the thumbnail bucket and the `ai-gateway` Edge Function.
The app works fully offline without any of this; with blank config, sync is simply switched off.

## 1. Create the project
1. Create a project at supabase.com (free plan). Note the **Project URL** and the **anon public key** (Settings > API).
2. Run `migrations/0001_init.sql` in the SQL editor (or `supabase db push` with the CLI). It creates the 15 synced
   tables, row level security (`user_id = auth.uid()`), the server-side `updated_at` trigger, the
   `(user_id, updated_at)` indexes and the private `thumbs` bucket with per-user policies.

## 2. Sign in with Google
1. Google Cloud Console: create an OAuth consent screen, then two OAuth clients:
   a **Web** client and an **Android** client (package `app.cove.companion` plus your debug/release SHA-1).
2. Supabase: Authentication > Providers > Google > enable, paste the **Web** client id and secret.
   Under Authentication > Providers, tick "Skip nonce checks" only if sign-in fails; the app sends a nonce.
3. Sign in once from the app, then copy your user id (Authentication > Users); you need it below.

## 3. Deploy the AI gateway
```
supabase functions deploy ai-gateway
supabase secrets set GEMINI_API_KEY=... GEMINI_MODEL=gemini-2.5-flash-lite \
  GEMINI_RETRY_MODEL=gemini-2.5-flash ALLOWED_USER_ID=<your user id>
```
Only your user id is served, journal content is rejected, and the model name can be changed without an app update.
In Google Cloud set a budget alert and a daily Gemini API quota (billing has no hard cap by default).
Tests: `deno test supabase/functions/ai-gateway`.

## 4. Put the keys into the app build
Add to `~/.gradle/gradle.properties` (never commit them):
```
supabaseUrl=https://<ref>.supabase.co
supabaseAnonKey=<anon key>
googleWebClientId=<web client id>.apps.googleusercontent.com
```
They become `BuildConfig.SUPABASE_URL`, `SUPABASE_ANON_KEY` and `GOOGLE_WEB_CLIENT_ID`. If any is blank the app hides
cloud features and keeps everything local.

## 5. Keep the free project awake
Free projects pause after a week of inactivity. Ping it weekly (a cron job, a GitHub Action, or any scheduler):
```
curl -s "$SUPABASE_URL/rest/v1/settings?select=id&limit=1" -H "apikey: $SUPABASE_ANON_KEY" -o /dev/null
```
(A 200 or 401 both count as activity.) Regular use of the app also keeps it active.
