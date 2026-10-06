-- Expenses imported from bank/UPI messages remember the bank reference (or a hash) so re-imports never double up.
alter table public.expenses add column if not exists external_ref text;
