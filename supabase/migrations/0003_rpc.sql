-- ============================================================================
--  Read RPCs. All SECURITY DEFINER + filter by auth.uid() so each caller sees
--  only data they are authorized to access. Column names are snake_case to
--  match the Android DTOs (@SerialName).
-- ============================================================================

create or replace function public.my_contacts()
returns table (connection_id uuid, user_id uuid, display_name text, status text)
language sql stable security definer set search_path = public as $$
    select c.connection_id,
           case when c.requester_id = auth.uid() then c.addressee_id else c.requester_id end,
           u.display_name,
           c.status
    from connections c
    join users u
        on u.id = case when c.requester_id = auth.uid() then c.addressee_id else c.requester_id end
    where (c.requester_id = auth.uid() or c.addressee_id = auth.uid())
      and c.status = 'ACCEPTED';
$$;

create or replace function public.incoming_requests()
returns table (connection_id uuid, from_user_id uuid, from_display_name text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
    select c.connection_id, c.requester_id, u.display_name, c.created_at
    from connections c
    join users u on u.id = c.requester_id
    where c.addressee_id = auth.uid() and c.status = 'PENDING'
    order by c.created_at desc;
$$;

create or replace function public.my_groups()
returns table (group_id uuid, name text, description text, image_url text,
               owner_id uuid, role text, member_count int, requires_approval boolean)
language sql stable security definer set search_path = public as $$
    select g.group_id, g.name, g.description, g.image_url, g.owner_id, gm.role,
           (select count(*)::int from group_members m
            where m.group_id = g.group_id and m.status = 'ACTIVE'),
           g.requires_approval
    from groups g
    join group_members gm
        on gm.group_id = g.group_id and gm.user_id = auth.uid() and gm.status = 'ACTIVE'
    order by g.name;
$$;

create or replace function public.group_members_of(p_group_id uuid)
returns table (user_id uuid, display_name text, role text, joined_at timestamptz)
language sql stable security definer set search_path = public as $$
    select m.user_id, u.display_name, m.role, m.joined_at
    from group_members m
    join users u on u.id = m.user_id
    where m.group_id = p_group_id
      and m.status = 'ACTIVE'
      and public.is_group_member(p_group_id, auth.uid())
    order by m.role, u.display_name;
$$;

create or replace function public.my_saved_messages()
returns table (message_id uuid, content text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
    select message_id, content, created_at
    from saved_messages
    where owner_id = auth.uid()
    order by created_at desc;
$$;

create or replace function public.my_history()
returns table (event_id uuid, sender_id uuid, sender_name text, destination_type text,
               destination_id uuid, destination_name text, content text, priority text,
               status text, created_at timestamptz, direction text,
               recipient_count int, acknowledged_count int, my_acknowledgment text)
language sql stable security definer set search_path = public as $$
    with mine as (
        select e.event_id, e.sender_id, e.destination_type, e.destination_id, e.destination_name,
               e.content, e.priority, e.status, e.created_at,
               'SENT'::text as direction, 'NONE'::text as my_ack
        from trigger_events e
        where e.sender_id = auth.uid()
        union all
        select e.event_id, e.sender_id, e.destination_type, e.destination_id, e.destination_name,
               e.content, e.priority, e.status, e.created_at,
               'RECEIVED'::text, er.acknowledgment_status
        from trigger_events e
        join event_recipients er on er.event_id = e.event_id
        where er.recipient_id = auth.uid()
    )
    select m.event_id, m.sender_id, su.display_name, m.destination_type, m.destination_id,
           m.destination_name, m.content, m.priority, m.status, m.created_at, m.direction,
           (select count(*)::int from event_recipients r where r.event_id = m.event_id),
           (select count(*)::int from event_recipients r
            where r.event_id = m.event_id and r.acknowledgment_status = 'ACKNOWLEDGED'),
           m.my_ack
    from mine m
    join users su on su.id = m.sender_id
    order by m.created_at desc;
$$;

create or replace function public.event_recipients_of(p_event_id uuid)
returns table (recipient_id uuid, display_name text, dispatch_status text,
               acknowledgment_status text, acknowledged_at timestamptz)
language sql stable security definer set search_path = public as $$
    select er.recipient_id, u.display_name, er.dispatch_status,
           er.acknowledgment_status, er.acknowledged_at
    from event_recipients er
    join users u on u.id = er.recipient_id
    where er.event_id = p_event_id
      and exists (select 1 from trigger_events e
                  where e.event_id = p_event_id and e.sender_id = auth.uid())
    order by u.display_name;
$$;

create or replace function public.my_personal_qr()
returns table (token text)
language sql stable security definer set search_path = public as $$
    select personal_invite_token from users where id = auth.uid();
$$;

-- Allow signed-in users to call the read RPCs.
grant execute on function public.my_contacts()                  to authenticated;
grant execute on function public.incoming_requests()            to authenticated;
grant execute on function public.my_groups()                    to authenticated;
grant execute on function public.group_members_of(uuid)         to authenticated;
grant execute on function public.my_saved_messages()            to authenticated;
grant execute on function public.my_history()                   to authenticated;
grant execute on function public.event_recipients_of(uuid)      to authenticated;
grant execute on function public.my_personal_qr()               to authenticated;
