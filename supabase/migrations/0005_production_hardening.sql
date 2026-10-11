-- Production hardening: device-aware abuse tracking and race-safe uniqueness.
alter table public.auth_attempts
    add column if not exists device_id text;

create index if not exists idx_auth_attempts_device_time
    on public.auth_attempts (device_id, created_at desc);

-- Prevent the same two users creating reverse-direction duplicate connections.
create unique index if not exists idx_connections_pair_unique
    on public.connections (
        least(requester_id, addressee_id),
        greatest(requester_id, addressee_id)
    );

-- Keep lookup and abuse-control queries bounded.
create index if not exists idx_audit_actor_action_time
    on public.audit_logs (actor_id, action, created_at desc);
