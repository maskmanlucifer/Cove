-- Training: exercises, the programme, sessions, logged sets, body weight and training settings.
-- Weights are kilograms; the unit in training_settings only changes how the app shows them.

create table public.exercises (
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
create policy exercises_owner on public.exercises for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger exercises_touch before insert or update on public.exercises
  for each row execute function public.cove_touch();
create index exercises_user_updated_idx on public.exercises (user_id, updated_at);

create table public.workout_plans (
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
create policy workout_plans_owner on public.workout_plans for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger workout_plans_touch before insert or update on public.workout_plans
  for each row execute function public.cove_touch();
create index workout_plans_user_updated_idx on public.workout_plans (user_id, updated_at);

create table public.plan_days (
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
create policy plan_days_owner on public.plan_days for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger plan_days_touch before insert or update on public.plan_days
  for each row execute function public.cove_touch();
create index plan_days_user_updated_idx on public.plan_days (user_id, updated_at);

create table public.workout_sessions (
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
create policy workout_sessions_owner on public.workout_sessions for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger workout_sessions_touch before insert or update on public.workout_sessions
  for each row execute function public.cove_touch();
create index workout_sessions_user_updated_idx on public.workout_sessions (user_id, updated_at);

create table public.set_logs (
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
create policy set_logs_owner on public.set_logs for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger set_logs_touch before insert or update on public.set_logs
  for each row execute function public.cove_touch();
create index set_logs_user_updated_idx on public.set_logs (user_id, updated_at);

create table public.body_weights (
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
create policy body_weights_owner on public.body_weights for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger body_weights_touch before insert or update on public.body_weights
  for each row execute function public.cove_touch();
create index body_weights_user_updated_idx on public.body_weights (user_id, updated_at);

create table public.training_settings (
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
create policy training_settings_owner on public.training_settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger training_settings_touch before insert or update on public.training_settings
  for each row execute function public.cove_touch();
create index training_settings_user_updated_idx on public.training_settings (user_id, updated_at);
