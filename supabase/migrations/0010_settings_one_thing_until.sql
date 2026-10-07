-- One-thing mode's switch-off time (epoch ms, 0 = until switched off). Settings rows are pushed whole, so the column must exist.
alter table public.settings add column if not exists one_thing_until bigint not null default 0;
