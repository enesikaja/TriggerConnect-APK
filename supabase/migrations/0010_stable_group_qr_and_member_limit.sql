-- One reusable QR per group and a hard 100-person group capacity.

-- Keep only the newest active GROUP invitation before adding the invariant.
with ranked as (
  select invitation_id,
         row_number() over (partition by target_id order by created_at desc, invitation_id desc) as rn
    from public.invitations
   where invitation_type = 'GROUP' and revoked = false
)
update public.invitations i
   set revoked = true
  from ranked r
 where i.invitation_id = r.invitation_id and r.rn > 1;

create unique index if not exists uq_invitations_one_active_group_qr
    on public.invitations (target_id)
    where invitation_type = 'GROUP' and revoked = false;

create or replace function public.ensure_group_invitation(
    p_group_id uuid,
    p_token_hash text,
    p_created_by uuid
) returns uuid
language plpgsql volatile security definer set search_path = public as $$
declare
    v_invitation_id uuid;
begin
    -- Serialize QR creation for the same group.
    perform 1 from public.groups where group_id = p_group_id for update;
    if not found then raise exception 'GROUP_NOT_FOUND'; end if;

    update public.invitations
       set revoked = true
     where invitation_type = 'GROUP'
       and target_id = p_group_id
       and revoked = false;

    select invitation_id into v_invitation_id
      from public.invitations
     where token_hash = p_token_hash
     limit 1;

    if v_invitation_id is null then
        insert into public.invitations (
            invitation_type, target_id, token_hash, created_by,
            expires_at, max_uses, usage_count, revoked
        ) values (
            'GROUP', p_group_id, p_token_hash, p_created_by,
            null, null, 0, false
        ) returning invitation_id into v_invitation_id;
    else
        update public.invitations
           set invitation_type = 'GROUP',
               target_id = p_group_id,
               created_by = p_created_by,
               expires_at = null,
               max_uses = null,
               revoked = false
         where invitation_id = v_invitation_id;
    end if;

    return v_invitation_id;
end;
$$;

revoke all on function public.ensure_group_invitation(uuid, text, uuid) from public, anon, authenticated;
grant execute on function public.ensure_group_invitation(uuid, text, uuid) to service_role;

create or replace function public.enforce_group_member_limit()
returns trigger
language plpgsql set search_path = public as $$
declare
    v_count integer;
begin
    if new.status not in ('ACTIVE', 'PENDING') then return new; end if;

    perform 1 from public.groups where group_id = new.group_id for update;
    if tg_op = 'INSERT' then
        select count(*) into v_count
          from public.group_members gm
         where gm.group_id = new.group_id
           and gm.status in ('ACTIVE', 'PENDING');
    else
        select count(*) into v_count
          from public.group_members gm
         where gm.group_id = new.group_id
           and gm.status in ('ACTIVE', 'PENDING')
           and gm.user_id <> old.user_id;
    end if;

    if v_count >= 100 then raise exception 'GROUP_FULL'; end if;
    return new;
end;
$$;

drop trigger if exists trg_group_member_limit on public.group_members;
create trigger trg_group_member_limit
before insert or update of group_id, status on public.group_members
for each row execute function public.enforce_group_member_limit();

create or replace function public.join_group_from_invitation(
    p_invitation_id uuid,
    p_user_id uuid
) returns table (result_group_id uuid, membership_status text, was_new boolean)
language plpgsql volatile security definer set search_path = public as $$
declare
    v_inv public.invitations%rowtype;
    v_group public.groups%rowtype;
    v_existing public.group_members%rowtype;
    v_status text;
    v_count integer;
begin
    select * into v_inv
      from public.invitations
     where invitation_id = p_invitation_id
       and invitation_type = 'GROUP'
       and revoked = false
       and (expires_at is null or expires_at > now())
       and (max_uses is null or usage_count < max_uses)
     for update;
    if not found then raise exception 'INVITE_INVALID'; end if;

    select * into v_group
      from public.groups
     where group_id = v_inv.target_id
     for update;
    if not found then raise exception 'GROUP_NOT_FOUND'; end if;

    select * into v_existing
      from public.group_members
     where group_id = v_group.group_id and user_id = p_user_id;

    if found and v_existing.status in ('ACTIVE', 'PENDING') then
        return query select v_group.group_id, v_existing.status, false;
        return;
    end if;

    select count(*) into v_count
      from public.group_members
     where group_id = v_group.group_id and status in ('ACTIVE', 'PENDING');
    if v_count >= 100 then raise exception 'GROUP_FULL'; end if;

    v_status := case when v_group.requires_approval then 'PENDING' else 'ACTIVE' end;
    insert into public.group_members (group_id, user_id, role, status, joined_at)
    values (v_group.group_id, p_user_id, 'MEMBER', v_status, now())
    on conflict (group_id, user_id) do update
       set role = 'MEMBER', status = excluded.status, joined_at = excluded.joined_at;

    update public.invitations
       set usage_count = usage_count + 1
     where invitation_id = p_invitation_id;

    return query select v_group.group_id, v_status, true;
end;
$$;

revoke all on function public.join_group_from_invitation(uuid, uuid) from public, anon, authenticated;
grant execute on function public.join_group_from_invitation(uuid, uuid) to service_role;
