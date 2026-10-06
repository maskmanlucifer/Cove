-- Training, simple model: a weekday plan (plan_exercises), per-date weight changes (day_overrides) and what was done
-- each day (exercise_logs). body_weights and training_settings (unit) stay. The 0005 tables exercises, workout_plans,
-- plan_days, workout_sessions and set_logs are no longer written by the app; they are left in place so older devices and
-- existing data are not lost (drop them by hand once every device is updated). Weights are kilograms.

create table public.plan_exercises (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  weekday integer not null,
  name text not null,
  weight_kg double precision not null default 0,
  sets integer not null default 3,
  reps integer not null default 8,
  increment_kg double precision not null default 2.5,
  sort integer not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.plan_exercises enable row level security;
create policy plan_exercises_owner on public.plan_exercises for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger plan_exercises_touch before insert or update on public.plan_exercises
  for each row execute function public.cove_touch();
create index plan_exercises_user_updated_idx on public.plan_exercises (user_id, updated_at);

create table public.day_overrides (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  day bigint not null,
  plan_exercise_id text not null,
  weight_kg double precision,
  dismissed boolean not null default false,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.day_overrides enable row level security;
create policy day_overrides_owner on public.day_overrides for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger day_overrides_touch before insert or update on public.day_overrides
  for each row execute function public.cove_touch();
create index day_overrides_user_updated_idx on public.day_overrides (user_id, updated_at);

create table public.exercise_logs (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  day bigint not null,
  name text not null,
  weight_kg double precision not null,
  target_sets integer not null,
  target_reps integer not null,
  reps text not null default '',
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, id)
);

alter table public.exercise_logs enable row level security;
create policy exercise_logs_owner on public.exercise_logs for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger exercise_logs_touch before insert or update on public.exercise_logs
  for each row execute function public.cove_touch();
create index exercise_logs_user_updated_idx on public.exercise_logs (user_id, updated_at);
