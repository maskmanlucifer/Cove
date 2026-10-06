-- ---------------------------------------------------------------------------
-- 0006 Data API access (works whether or not "Automatically expose new tables" is on).
-- Signed-in users get row access limited by the RLS policies above; anonymous users get none.
-- Safe to run more than once.
-- ---------------------------------------------------------------------------
grant usage on schema public to authenticated;

do $$
declare
  t text;
begin
  foreach t in array array[
    'settings',
    'alarms',
    'todo_categories',
    'todos',
    'events',
    'habits',
    'habit_logs',
    'expense_categories',
    'expenses',
    'journal_entries',
    'journal_media',
    'decisions',
    'suggestion_prefs',
    'voice_commands',
    'briefs',
    'category_memory',
    'exercises',
    'workout_plans',
    'plan_days',
    'workout_sessions',
    'set_logs',
    'body_weights',
    'training_settings'
  ] loop
    execute format('revoke all on table public.%I from anon', t);
    execute format('grant select, insert, update, delete on table public.%I to authenticated', t);
  end loop;
end $$;
