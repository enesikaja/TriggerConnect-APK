-- Show pending membership requests only to owners/admins.
drop function if exists public.group_members_of(uuid);
create function public.group_members_of(p_group_id uuid)
returns table (user_id uuid, display_name text, role text, status text, joined_at timestamptz)
language sql stable security definer set search_path = public as $$
    select m.user_id, u.display_name, m.role, m.status, m.joined_at
    from group_members m
    join users u on u.id = m.user_id
    where m.group_id = p_group_id
      and public.is_group_member(p_group_id, auth.uid())
      and (
          m.status = 'ACTIVE'
          or (
              m.status = 'PENDING'
              and public.group_role_of(p_group_id, auth.uid()) in ('OWNER', 'ADMIN')
          )
      )
    order by case when m.status = 'PENDING' then 0 else 1 end, m.role, u.display_name;
$$;

revoke execute on function public.group_members_of(uuid) from public, anon;
grant execute on function public.group_members_of(uuid) to authenticated;
