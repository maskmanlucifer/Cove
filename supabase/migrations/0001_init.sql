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
create table public.settings (
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

create table public.alarms (
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

create table public.todo_categories (
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

create table public.todos (
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

create table public.events (
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

create table public.habits (
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

create table public.habit_logs (
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

create table public.expense_categories (
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

create table public.expenses (
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

create table public.journal_entries (
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

create table public.journal_media (
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

create table public.decisions (
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

create table public.suggestion_prefs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null,
  muted boolean not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, kind)
);

create table public.voice_commands (
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

create table public.briefs (
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
    execute format(
      'create policy %I on public.%I for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid())',
      t || '_owner', t);
    execute format(
      'create trigger %I before insert or update on public.%I for each row execute function public.cove_touch()',
      t || '_touch', t);
    execute format('create index %I on public.%I (user_id, updated_at)', t || '_user_updated_idx', t);
  end loop;
end
$$;

-- Thumbnails (journal media lives on Google Drive; only small thumbs are stored here).
-- Objects are named <user id>/<file>, so the first folder must be the caller's own id.
insert into storage.buckets (id, name, public)
values ('thumbs', 'thumbs', false)
on conflict (id) do nothing;

create policy thumbs_read_own on storage.objects for select to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy thumbs_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy thumbs_update_own on storage.objects for update to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy thumbs_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'thumbs' and (storage.foldername(name))[1] = auth.uid()::text);
