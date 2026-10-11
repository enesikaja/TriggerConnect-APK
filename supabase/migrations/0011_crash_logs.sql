-- Remote crash telemetry. Written only by the report-crash Edge Function
-- (service role); RLS is on with no policies so clients cannot read/write it.
create table if not exists public.crash_logs (
    id          uuid primary key default gen_random_uuid(),
    created_at  timestamptz not null default now(),
    app_version text,
    device      text,
    android_sdk integer,
    thread      text,
    message     text,
    stacktrace  text
);

alter table public.crash_logs enable row level security;
create index if not exists idx_crash_logs_time on public.crash_logs (created_at desc);
