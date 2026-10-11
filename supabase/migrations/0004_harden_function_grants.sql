-- ============================================================================
--  Hardening (from the security advisor): SECURITY DEFINER functions are
--  EXECUTE-able by PUBLIC/anon by default. Internal helpers must not be exposed
--  via the REST API, and read RPCs should be callable by signed-in users only.
-- ============================================================================

-- Internal helpers — called only by other SECURITY DEFINER functions (which run
-- as the owner), never directly through /rest/v1/rpc.
revoke all on function public.is_group_member(uuid, uuid) from public, anon, authenticated;
revoke all on function public.group_role_of(uuid, uuid) from public, anon, authenticated;

-- Read RPCs — drop the implicit PUBLIC/anon EXECUTE; keep `authenticated` only.
revoke execute on function public.my_contacts()             from public, anon;
revoke execute on function public.incoming_requests()       from public, anon;
revoke execute on function public.my_groups()               from public, anon;
revoke execute on function public.group_members_of(uuid)    from public, anon;
revoke execute on function public.my_saved_messages()       from public, anon;
revoke execute on function public.my_history()              from public, anon;
revoke execute on function public.event_recipients_of(uuid) from public, anon;
revoke execute on function public.my_personal_qr()          from public, anon;

grant execute on function public.my_contacts()             to authenticated;
grant execute on function public.incoming_requests()       to authenticated;
grant execute on function public.my_groups()               to authenticated;
grant execute on function public.group_members_of(uuid)    to authenticated;
grant execute on function public.my_saved_messages()       to authenticated;
grant execute on function public.my_history()              to authenticated;
grant execute on function public.event_recipients_of(uuid) to authenticated;
grant execute on function public.my_personal_qr()          to authenticated;
