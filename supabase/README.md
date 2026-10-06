# Cove backend (Supabase)

Setup is described in [`docs/SETUP.md`](../docs/SETUP.md). In short: create a free project, run `supabase/setup.sql` once in the SQL editor, and paste the Project URL and anon key into **Me > Connect services**.

- `setup.sql`: all migrations combined and idempotent (a copy lives in `app/src/main/assets/setup.sql`; a unit test keeps them equal).
- `migrations/`: the same schema as separate steps (`supabase db push` also works): 21 synced tables with row level security (`user_id = auth.uid()`), a server-side `updated_at` trigger, sync-cursor indexes and the private `thumbs` bucket.
- `functions/ai-gateway`: optional legacy Edge Function; see "Advanced: server-side key" in `docs/SETUP.md`. The app calls Gemini directly by default.

## Keep the free project awake
Free projects pause after a week of inactivity. Regular use of the app keeps it active; otherwise ping it weekly:
```
curl -s "$SUPABASE_URL/rest/v1/settings?select=id&limit=1" -H "apikey: $SUPABASE_ANON_KEY" -o /dev/null
```
