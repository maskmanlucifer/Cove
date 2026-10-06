-- App-lock preferences are device-local, but settings rows are pushed whole, so the columns must exist.
alter table public.settings add column if not exists lock_after text not null default '1min';
alter table public.settings add column if not exists hide_in_recents boolean not null default true;
