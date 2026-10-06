-- Smart expense categorisation: words the user files under a category, and what Cove has learned from corrections.
alter table public.expense_categories add column if not exists keywords text not null default '';

-- category_memory: one row per word (token) with the category it is filed under and how many times.
create table public.category_memory (
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
create policy category_memory_owner on public.category_memory for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger category_memory_touch before insert or update on public.category_memory
  for each row execute function public.cove_touch();
create index category_memory_user_updated_idx on public.category_memory (user_id, updated_at);
