-- Connection mutations are privileged operations and must pass Edge Function
-- authorization. Authenticated clients retain read-only access to their rows.
revoke update, delete on public.connections from authenticated;
drop policy if exists connections_update_participant on public.connections;
drop policy if exists connections_delete_participant on public.connections;

-- Profiles are client-editable only for the display name.
revoke update on public.users from authenticated;
grant update (display_name) on public.users to authenticated;
