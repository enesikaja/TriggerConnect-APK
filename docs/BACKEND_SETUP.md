# Backend setup (Supabase + FCM)

The backend is **100% free tier**: Supabase (Postgres + Auth + Edge Functions) for data/logic, Firebase Cloud Messaging for push delivery only.

## 1. Create the Supabase project

1. Create a project at <https://supabase.com> (free tier). Note the **Project URL** and the **anon** and **service_role** keys (Project Settings ▸ API).
2. Install and log in to the CLI:
   ```bash
   npm i -g supabase
   supabase login
   supabase link --project-ref <your-project-ref>
   ```

## 2. Apply the database schema

```bash
supabase db push        # runs supabase/migrations/0001_init.sql, 0002_rls.sql, 0003_rpc.sql
```
This creates all tables, indexes, Row-Level Security policies, and the read RPCs.

> **Auth provider:** the app uses an internal email/password *bootstrap* only to mint real sessions from the six-digit code — no email is ever sent. In Authentication ▸ Providers, ensure **Email** is enabled and **"Confirm email"** is **off** (accounts are created pre-confirmed by the Edge Functions).

## 3. Set Edge Function secrets

Copy the example and fill it in (never commit the real file):
```bash
cp supabase/functions/.env.example supabase/functions/.env
```
Generate the two keyed-hash secrets:
```bash
openssl rand -hex 32   # -> CODE_HASH_SECRET
openssl rand -hex 32   # -> INVITE_HASH_SECRET
```
`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically at runtime — you don't set them.

## 4. Firebase Cloud Messaging (free)

1. Create a Firebase project (Spark/free plan is sufficient for FCM).
2. **Android app:** add an app with package `com.triggerconnect.app`, download `google-services.json`, and place it in `app/`.
3. **Server credentials for sending:** Project Settings ▸ Service accounts ▸ *Generate new private key*. This downloads a JSON. Put its **entire contents** into the `FCM_SERVICE_ACCOUNT` secret (single line). The Edge Function uses it to call the FCM HTTP v1 API — this is free and needs no Blaze plan.

Then push the secrets:
```bash
supabase secrets set --env-file supabase/functions/.env
```

## 5. Deploy the Edge Functions

```bash
supabase functions deploy
```
Functions deployed: `auth-register`, `auth-login`, `register-device`, `connection-request`, `connection-respond`, `group-invite-create`, `group-manage`, `invite-resolve`, `group-join`, `trigger-dispatch`, `event-acknowledge`, `account-delete`.

`config.toml` sets `verify_jwt = false` for `auth-register`/`auth-login` (called before a session exists); all others require a valid user JWT at the gateway and re-check the user inside.

## 6. Point the app at the backend

Create `local.properties` in the repo root:
```properties
SUPABASE_URL=https://YOUR-PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
```
(These are injected into `BuildConfig` by `app/build.gradle.kts`.)

## Verifying

- `supabase functions list` shows all 13.
- Register in the app → a `users` row + `user_credentials` row appear.
- Send a trigger → a `trigger_events` row + `event_recipients` rows appear; recipients with a registered device get a push.

## Optional: provisioning via MCP

If you use the Supabase connector in this environment, the project, migrations, and functions can be provisioned programmatically (create project → apply migrations → deploy functions) instead of running the CLI by hand.
