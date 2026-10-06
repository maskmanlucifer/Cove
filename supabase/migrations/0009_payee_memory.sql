-- Payee memory: what the user taught Cove about one payee (a UPI handle or a normalised merchant name), so the next
-- payment to the same payee is tagged like the last one. Expenses imported from messages carry their payee_key.
alter table public.expenses add column if not exists payee_key text;

-- payee_memory: one row per payee_key with its category, the label (note) to show, the cleaned name and a count.
create table public.payee_memory (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  payee_key text not null,
  category_id text not null,
  label text,
  display_name text not null default '',
  count integer not null default 1,
  updated_at bigint not null default 0,
  deleted_at bigint,
  device_id text,
  device_name text,
  primary key (user_id, payee_key)
);

alter table public.payee_memory enable row level security;
create policy payee_memory_owner on public.payee_memory for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger payee_memory_touch before insert or update on public.payee_memory
  for each row execute function public.cove_touch();
create index payee_memory_user_updated_idx on public.payee_memory (user_id, updated_at);

-- Data API access for the new table (same rule as 0006_data_api_grants).
revoke all on table public.payee_memory from anon;
grant select, insert, update, delete on table public.payee_memory to authenticated;
