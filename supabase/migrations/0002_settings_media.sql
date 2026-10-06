-- Photo quality and upload preference sync with the rest of settings.
alter table public.settings add column if not exists photo_quality text not null default 'balanced';
alter table public.settings add column if not exists upload_on_wifi_only boolean not null default true;
