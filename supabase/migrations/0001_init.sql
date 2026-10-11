-- ============================================================================
--  Trigger Connect — core schema
--  Reads are served by SECURITY DEFINER RPCs (0003). Privileged writes are
--  performed by Edge Functions using the service role. Direct table access by
--  the `authenticated` role is locked down by RLS (0002).
-- ============================================================================

create extension if not exists pgcrypto;

-- Profiles (1:1 with auth.users). The six-digit code is NOT stored here.
create table if not exists public.users (
    id                    uuid primary key references auth.users (id) on delete cascade,
    display_name          text not null check (char_length(display_name) between 2 and 40),
    status                text not null default 'ACTIVE',
    personal_invite_token text not null unique default encode(gen_random_bytes(18), 'hex'),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

-- Keyed hash of the login code (never the plaintext). code_hash = HMAC(code, secret).
create table if not exists public.user_credentials (
    user_id    uuid primary key references public.users (id) on delete cascade,
    code_hash  text not null unique,
    created_at timestamptz not null default now()
);

-- Registered push devices.
create table if not exists public.devices (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references public.users (id) on delete cascade,
    fcm_token      text not null unique,
    platform       text not null default 'android',
    last_active_at timestamptz not null default now(),
    created_at     timestamptz not null default now()
);

-- One-to-one connections.
create table if not exists public.connections (
    connection_id uuid primary key default gen_random_uuid(),
    requester_id  uuid not null references public.users (id) on delete cascade,
    addressee_id  uuid not null references public.users (id) on delete cascade,
    status        text not null default 'PENDING', -- PENDING | ACCEPTED | BLOCKED
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    check (requester_id <> addressee_id),
    unique (requester_id, addressee_id)
);

create table if not exists public.groups (
    group_id          uuid primary key default gen_random_uuid(),
    name              text not null check (char_length(name) between 2 and 60),
    description       text,
    image_url         text,
    owner_id          uuid not null references public.users (id) on delete cascade,
    requires_approval boolean not null default false,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create table if not exists public.group_members (
    group_id  uuid not null references public.groups (group_id) on delete cascade,
    user_id   uuid not null references public.users (id) on delete cascade,
    role      text not null default 'MEMBER', -- OWNER | ADMIN | MEMBER
    status    text not null default 'ACTIVE', -- ACTIVE | PENDING
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create table if not exists public.invitations (
    invitation_id   uuid primary key default gen_random_uuid(),
    invitation_type text not null default 'GROUP',
    target_id       uuid not null,              -- group_id for GROUP invites
    token_hash      text not null unique,       -- HMAC of the opaque token
    created_by      uuid not null references public.users (id) on delete cascade,
    expires_at      timestamptz,
    max_uses        integer,
    usage_count     integer not null default 0,
    revoked         boolean not null default false,
    created_at      timestamptz not null default now()
);

create table if not exists public.saved_messages (
    message_id uuid primary key default gen_random_uuid(),
    owner_id   uuid not null references public.users (id) on delete cascade,
    content    text not null check (char_length(content) between 1 and 280),
    created_at timestamptz not null default now()
);

create table if not exists public.trigger_events (
    event_id         uuid primary key default gen_random_uuid(),
    sender_id        uuid not null references public.users (id) on delete cascade,
    client_event_id  text not null,
    destination_type text not null,             -- USER | GROUP
    destination_id   uuid not null,
    destination_name text not null,
    content          text not null check (char_length(content) between 1 and 280),
    priority         text not null default 'NORMAL',
    status           text not null default 'CREATED',
    created_at       timestamptz not null default now(),
    unique (sender_id, client_event_id)          -- idempotency guard
);

create table if not exists public.event_recipients (
    id                   uuid primary key default gen_random_uuid(),
    event_id             uuid not null references public.trigger_events (event_id) on delete cascade,
    recipient_id         uuid not null references public.users (id) on delete cascade,
    dispatch_status      text not null default 'PENDING',  -- PENDING | ACCEPTED | FAILED | SKIPPED
    acknowledgment_status text not null default 'NONE',    -- NONE | ACKNOWLEDGED
    acknowledged_at      timestamptz,
    created_at           timestamptz not null default now(),
    unique (event_id, recipient_id)
);

create table if not exists public.audit_logs (
    id         uuid primary key default gen_random_uuid(),
    actor_id   uuid,
    action     text not null,
    target_id  text,
    metadata   jsonb,
    created_at timestamptz not null default now()
);

-- Login attempt log for rate limiting / lockouts (populated by auth-login).
create table if not exists public.auth_attempts (
    id         uuid primary key default gen_random_uuid(),
    ip         text,
    success    boolean not null default false,
    created_at timestamptz not null default now()
);

-- -------------------- Indexes --------------------
create index if not exists idx_connections_requester on public.connections (requester_id);
create index if not exists idx_connections_addressee on public.connections (addressee_id);
create index if not exists idx_group_members_user on public.group_members (user_id);
create index if not exists idx_group_members_group on public.group_members (group_id);
create index if not exists idx_events_sender_created on public.trigger_events (sender_id, created_at desc);
create index if not exists idx_event_recipients_recipient on public.event_recipients (recipient_id);
create index if not exists idx_event_recipients_event on public.event_recipients (event_id);
create index if not exists idx_devices_user on public.devices (user_id);
create index if not exists idx_invitations_token on public.invitations (token_hash);
create index if not exists idx_saved_messages_owner on public.saved_messages (owner_id);
create index if not exists idx_auth_attempts_ip_time on public.auth_attempts (ip, created_at desc);
