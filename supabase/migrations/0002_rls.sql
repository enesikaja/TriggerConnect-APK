-- ============================================================================
--  Row-Level Security.
--  Default posture: RLS ON for every table. Direct access by `authenticated`
--  is granted ONLY where the client genuinely needs it (self profile, own
--  saved messages, own connections). Everything else is reached through
--  SECURITY DEFINER RPCs (reads) or Edge Functions with the service role
--  (writes), which bypass RLS and perform their own authorization.
-- ============================================================================

alter table public.users            enable row level security;
alter table public.user_credentials enable row level security;
alter table public.devices          enable row level security;
alter table public.connections      enable row level security;
alter table public.groups           enable row level security;
alter table public.group_members    enable row level security;
alter table public.invitations      enable row level security;
alter table public.saved_messages   enable row level security;
alter table public.trigger_events   enable row level security;
alter table public.event_recipients enable row level security;
alter table public.audit_logs       enable row level security;
alter table public.auth_attempts    enable row level security;

-- -------------------- Helper functions --------------------
create or replace function public.is_group_member(p_group_id uuid, p_user_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists(
        select 1 from group_members
        where group_id = p_group_id and user_id = p_user_id and status = 'ACTIVE'
    );
$$;

create or replace function public.group_role_of(p_group_id uuid, p_user_id uuid)
returns text language sql stable security definer set search_path = public as $$
    select role from group_members
    where group_id = p_group_id and user_id = p_user_id and status = 'ACTIVE';
$$;

-- -------------------- Grants (minimal) --------------------
grant select, update on public.users to authenticated;
grant select, insert, update, delete on public.saved_messages to authenticated;
grant select, update, delete on public.connections to authenticated;

-- -------------------- Policies --------------------

-- users: a user may see and update only their own profile.
drop policy if exists users_select_self on public.users;
create policy users_select_self on public.users
    for select to authenticated using (id = auth.uid());

drop policy if exists users_update_self on public.users;
create policy users_update_self on public.users
    for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- saved_messages: full CRUD limited to the owner.
drop policy if exists saved_owner_all on public.saved_messages;
create policy saved_owner_all on public.saved_messages
    for all to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

-- connections: participants can read; participants can update (block) or delete
-- (remove). Creation of a connection happens only via the Edge Function.
drop policy if exists connections_select_participant on public.connections;
create policy connections_select_participant on public.connections
    for select to authenticated
    using (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists connections_update_participant on public.connections;
create policy connections_update_participant on public.connections
    for update to authenticated
    using (auth.uid() = requester_id or auth.uid() = addressee_id)
    with check (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists connections_delete_participant on public.connections;
create policy connections_delete_participant on public.connections
    for delete to authenticated
    using (auth.uid() = requester_id or auth.uid() = addressee_id);

-- No policies are defined for: user_credentials, devices, groups, group_members,
-- invitations, trigger_events, event_recipients, audit_logs, auth_attempts.
-- With RLS enabled and no policy, the `authenticated` role is denied all direct
-- access; those tables are reached only via RPCs / Edge Functions.
