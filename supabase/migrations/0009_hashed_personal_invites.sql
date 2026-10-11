drop function if exists public.my_personal_qr();
alter table public.users drop column if exists personal_invite_token;
