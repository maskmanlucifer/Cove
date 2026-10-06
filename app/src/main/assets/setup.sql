-- Cove: complete Supabase setup in one paste (migrations 0001, 0002, 0003 and 0004 combined).
-- Run it once in the Supabase dashboard: SQL Editor > New query > paste > Run.
-- Safe to run again: every statement is idempotent.
-- Keep in sync with supabase/migrations/*.sql (a unit test checks the copy in app/src/main/assets).

-- ===== 0001_init =====
-- Cove cloud sync schema. Mirrors the Room entities in
-- app/src/main/kotlin/app/cove/companion/data/local/entity/Entities.kt.
--
-- Conventions
--   * Times are bigint epoch milliseconds, exactly like Room, so rows round-trip unchanged.
--   * updated_at is stamped by the server on every insert/update (trigger below); it is the pull cursor.
--   * deleted_at is a soft delete so removals sync.
--   * device_id / device_name say which install wrote a row (used by the app to tell its own echo from
--     another device's change and to label conflicts).
--   * Primary key is (user_id, <key>) so two users may never collide; the app upserts with
--     on_conflict=user_id,<key>.
--   * No foreign keys: rows from one batch may arrive in any order.

create or replace function public.cove_touch() returns trigger
language plpgsql as $$
begin
  new.updated_at := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end;
$$;

-- settings: one row per user (id is always 'me')
create table if not exists public.settings (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null default 'me',
  display_name text not null default '',
  wake_minutes integer not null default 390,
  theme text not null default 'system',
  text_scale real not null default 1,
  spoken_replies boolean not null default true,
  reduce_motion text not null default 'system',
  nudge_mode text not null default 'bundled',
  one_thing_mode boolean not null default false,
  brief_on boolean not null default true,
  suggestions_on boolean not null default true,
  biometric_lock boolean not null default false,
  onboarded boolean not null default false,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.alarms (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  label text not null,
  minutes integer not null,
  days_mask integer not null default 0,
  kind text not null default 'wake',
  sound text not null default 'Soft rise',
  gentle_rise boolean not null default true,
  snooze_minutes integer not null default 9,
  enabled boolean not null default true,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.todo_categories (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.todos (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  category_id text,
  title text not null,
  due_at bigint,
  remind boolean not null default false,
  done boolean not null default false,
  done_at bigint,
  sort integer not null default 0,
  source text not null default 'manual',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.events (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  title text not null,
  start_at bigint not null,
  end_at bigint,
  place text,
  notes text,
  repeat text not null default 'none',
  remind_before_min integer,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.habits (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  cadence text not null default 'daily',
  days_mask integer not null default 127,
  remind_minutes integer,
  after_wake_up boolean not null default false,
  show_on_today boolean not null default true,
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.habit_logs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  habit_id text not null,
  day bigint not null,
  count integer not null default 1,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.expense_categories (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  kind text not null default 'spending',
  budget_paise bigint not null default 0,
  carry_over boolean not null default false,
  alert_at80 boolean not null default true,
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.expenses (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  amount_paise bigint not null,
  kind text not null default 'spent',
  category_id text,
  note text not null default '',
  paid_with text not null default 'UPI',
  spent_at bigint not null,
  source text not null default 'manual',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.journal_entries (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  day bigint not null,
  title text not null default '',
  body text not null default '',
  mood text,
  created_at bigint not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.journal_media (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  entry_id text not null,
  kind text not null,
  local_path text not null default '',
  thumb_path text,
  duration_ms bigint,
  bytes bigint not null default 0,
  upload_state text not null default 'pending',
  drive_file_id text,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.decisions (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  kind text not null,
  title text not null,
  body text not null,
  reasons text not null,
  status text not null default 'shown',
  created_at bigint not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.suggestion_prefs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null,
  muted boolean not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, kind)
);

create table if not exists public.voice_commands (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  transcript text not null,
  intent text not null,
  undo_payload text,
  undone boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

create table if not exists public.briefs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day bigint not null,
  segments text not null,
  generated_at bigint not null,
  duration_sec integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, day)
);

-- Row level security, server-stamped updated_at and the sync cursor index on every table.
do $$
declare t text;
begin
  foreach t in array array[
    'settings', 'alarms', 'todo_categories', 'todos', 'events', 'habits', 'habit_logs',
    'expense_categories', 'expenses', 'journal_entries', 'journal_media', 'decisions',
    'suggestion_prefs', 'voice_commands', 'briefs'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner', t);
    execute format(
      'create policy %I on public.%I for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid())',
      t || '_owner', t);
    execute format('drop trigger if exists %I on public.%I', t || '_touch', t);
    execute format(
      'create trigger %I before insert or update on public.%I for each row execute function public.cove_touch()',
      t || '_touch', t);
    execute format('create index if not exists %I on public.%I (user_id, updated_at)', t || '_user_updated_idx', t);
  end loop;
end
$$;

-- Thumbnails (journal media lives on Google Drive; only small thumbs are stored here).
-- Objects are named <user id>/<file>, so the first folder must be the caller's own id.
insert into storage.buckets (id, name, public)
values ('thumbs', 'thumbs', false)
on conflict (id) do nothing;

drop policy if exists thumbs_read_own on storage.objects;
create policy thumbs_read_own on storage.objects for select to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists thumbs_insert_own on storage.objects;
create policy thumbs_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists thumbs_update_own on storage.objects;
create policy thumbs_update_own on storage.objects for update to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists thumbs_delete_own on storage.objects;
create policy thumbs_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);

-- ===== 0002_settings_media =====
-- Photo quality and upload preference sync with the rest of settings.
alter table public.settings add column if not exists photo_quality text not null default 'balanced';
alter table public.settings add column if not exists upload_on_wifi_only boolean not null default true;

-- ===== 0003_settings_lock =====
-- App-lock preferences are device-local, but settings rows are pushed whole, so the columns must exist.
alter table public.settings add column if not exists lock_after text not null default '1min';
alter table public.settings add column if not exists hide_in_recents boolean not null default true;

-- ===== 0004_categorize =====
-- Smart expense categorisation: words the user files under a category, and what Cove has learned from corrections.
alter table public.expense_categories add column if not exists keywords text not null default '';

-- category_memory: one row per word (token) with the category it is filed under and how many times.
create table if not exists public.category_memory (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  token text not null,
  category_id text not null,
  count integer not null default 1,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, token)
);

alter table public.category_memory enable row level security;
drop policy if exists category_memory_owner on public.category_memory;
create policy category_memory_owner on public.category_memory for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists category_memory_touch on public.category_memory;
create trigger category_memory_touch before insert or update on public.category_memory
  for each row execute function public.cove_touch();
create index if not exists category_memory_user_updated_idx on public.category_memory (user_id, updated_at);

-- ===== 0005_training =====
-- Training: exercises, the programme, sessions, logged sets, body weight and training settings.
-- Weights are kilograms; the unit in training_settings only changes how the app shows them.

create table if not exists public.exercises (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  muscle_group text not null default 'other',
  kind text not null default 'weighted',
  increment_kg double precision not null default 2.5,
  rep_min integer not null default 8,
  rep_max integer not null default 8,
  sets integer not null default 3,
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.exercises enable row level security;
drop policy if exists exercises_owner on public.exercises;
create policy exercises_owner on public.exercises for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists exercises_touch on public.exercises;
create trigger exercises_touch before insert or update on public.exercises
  for each row execute function public.cove_touch();
create index if not exists exercises_user_updated_idx on public.exercises (user_id, updated_at);

create table if not exists public.workout_plans (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  name text not null default 'Push Pull Legs',
  days_per_week integer not null default 3,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.workout_plans enable row level security;
drop policy if exists workout_plans_owner on public.workout_plans;
create policy workout_plans_owner on public.workout_plans for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists workout_plans_touch on public.workout_plans;
create trigger workout_plans_touch before insert or update on public.workout_plans
  for each row execute function public.cove_touch();
create index if not exists workout_plans_user_updated_idx on public.workout_plans (user_id, updated_at);

create table if not exists public.plan_days (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  plan_id text not null,
  day_type text not null,
  exercise_ids text not null default '',
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.plan_days enable row level security;
drop policy if exists plan_days_owner on public.plan_days;
create policy plan_days_owner on public.plan_days for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists plan_days_touch on public.plan_days;
create trigger plan_days_touch before insert or update on public.plan_days
  for each row execute function public.cove_touch();
create index if not exists plan_days_user_updated_idx on public.plan_days (user_id, updated_at);

create table if not exists public.workout_sessions (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  day_type text not null,
  planned_at bigint not null,
  started_at bigint,
  ended_at bigint,
  note text not null default '',
  exercise_ids text not null default '',
  skipped_ids text not null default '',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.workout_sessions enable row level security;
drop policy if exists workout_sessions_owner on public.workout_sessions;
create policy workout_sessions_owner on public.workout_sessions for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists workout_sessions_touch on public.workout_sessions;
create trigger workout_sessions_touch before insert or update on public.workout_sessions
  for each row execute function public.cove_touch();
create index if not exists workout_sessions_user_updated_idx on public.workout_sessions (user_id, updated_at);

create table if not exists public.set_logs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  session_id text not null,
  exercise_id text not null,
  set_no integer not null,
  weight_kg double precision not null,
  reps integer not null,
  logged_at bigint not null,
  source text not null default 'manual',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.set_logs enable row level security;
drop policy if exists set_logs_owner on public.set_logs;
create policy set_logs_owner on public.set_logs for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists set_logs_touch on public.set_logs;
create trigger set_logs_touch before insert or update on public.set_logs
  for each row execute function public.cove_touch();
create index if not exists set_logs_user_updated_idx on public.set_logs (user_id, updated_at);

create table if not exists public.body_weights (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day bigint not null,
  kg double precision not null,
  note text not null default '',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, day)
);

alter table public.body_weights enable row level security;
drop policy if exists body_weights_owner on public.body_weights;
create policy body_weights_owner on public.body_weights for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists body_weights_touch on public.body_weights;
create trigger body_weights_touch before insert or update on public.body_weights
  for each row execute function public.cove_touch();
create index if not exists body_weights_user_updated_idx on public.body_weights (user_id, updated_at);

create table if not exists public.training_settings (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null default 'me',
  unit text not null default 'kg',
  days_per_week integer not null default 3,
  rest_seconds integer not null default 90,
  weekdays text not null default '1,3,5',
  start_minutes integer not null default 1140,
  start_weights text not null default '',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.training_settings enable row level security;
drop policy if exists training_settings_owner on public.training_settings;
create policy training_settings_owner on public.training_settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists training_settings_touch on public.training_settings;
create trigger training_settings_touch before insert or update on public.training_settings
  for each row execute function public.cove_touch();
create index if not exists training_settings_user_updated_idx on public.training_settings (user_id, updated_at);

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
