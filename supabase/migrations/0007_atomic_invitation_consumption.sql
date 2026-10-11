create or replace function public.consume_group_invitation(p_invitation_id uuid)
returns setof public.invitations
language sql volatile security definer set search_path = public as $$
    update public.invitations
       set usage_count = usage_count + 1
     where invitation_id = p_invitation_id
       and invitation_type = 'GROUP'
       and revoked = false
       and (expires_at is null or expires_at > now())
       and (max_uses is null or usage_count < max_uses)
    returning *;
$$;

revoke all on function public.consume_group_invitation(uuid) from public, anon, authenticated;
grant execute on function public.consume_group_invitation(uuid) to service_role;
