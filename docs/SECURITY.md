# Security model

## Authentication (six-digit code)

- Codes are generated server-side with a **CSPRNG** using rejection sampling for a uniform distribution (`supabase/functions/_shared/crypto.ts`).
- Only a **keyed HMAC-SHA256** of the code is stored (`user_credentials.code_hash`), using the `CODE_HASH_SECRET` Edge secret. Plaintext codes are never stored, logged, put in analytics, notification payloads, or QR codes.
- A six-digit code has only 10⁶ possibilities, so the code is **never** validated on the client and brute force is mitigated server-side:
  - **Rate limiting + lockout** per IP (`auth_attempts`, 10 failures / 15 min → `429`).
  - Login runs only in the `auth-login` Edge Function (service role).
- Sessions use real Supabase GoTrue tokens, obtained via a **one-time bootstrap**: the function sets a fresh random password and returns it once; the client immediately exchanges it for a session (real access + refresh tokens). Tokens are persisted in **Android Keystore-backed** `EncryptedSharedPreferences` (`SecureSessionManager`), never plaintext.

## Authorization

- **Reads:** base tables have RLS enabled; the `authenticated` role can touch only its own profile, own saved messages, and its own connections. Everything else is read through **SECURITY DEFINER RPCs** that filter by `auth.uid()` — so callers only ever see data they're entitled to.
- **Writes:** all privileged mutations go through **Edge Functions** using the service role, each performing explicit checks (contact accepted, active group membership, owner/admin role, etc.).
- Group roles (OWNER/ADMIN/MEMBER) are enforced in `group-manage`; the client mirror is UI-only.

## QR invitations

- Payloads are **opaque random tokens**, never credentials. Personal tokens identify a user for a connection request; group tokens are stored only as an **HMAC** (`invitations.token_hash`).
- Group invites support **expiry**, **max-uses**, **revoke/rotate**, and optional **join approval**. Expired/revoked/exhausted invites never grant access. Scanning never auto-enrolls — the user confirms a resolved preview first (`invite-resolve` → `group-join`).

## Trigger dispatch

- Spoofing is prevented: the sender is the authenticated user; recipients are resolved from **current authorized membership** server-side.
- **Idempotency:** a client-generated `clientEventId` plus a `unique(sender_id, client_event_id)` constraint means double-taps/retries never create duplicates.
- Group fan-out notifies all active members **except the sender** unless self-notify is enabled; direct triggers notify only the one contact.
- Push is sent **only from the backend** via FCM HTTP v1 with a service account — there is **no FCM server key in the APK**.
- Invalid/expired device tokens are cleaned up automatically (`UNREGISTERED` → row deleted).

## Delivery honesty

The UI distinguishes **"accepted by push provider"** from **"acknowledged by recipient"** and never claims verified device delivery or delivery under battery-saver/force-stop. Dispatch statuses: `ACCEPTED` / `FAILED` / `SKIPPED` (no device).

## Secrets

- No secrets are committed. `.gitignore` excludes keystores, `keystore.properties`, `google-services.json`, `service-account*.json`, and `.env`.
- Edge secrets (`CODE_HASH_SECRET`, `INVITE_HASH_SECRET`, `FCM_SERVICE_ACCOUNT`) live only in Supabase. CI uses GitHub Actions secrets with least privilege.

## Suggested test cases (see acceptance criteria)

Unauthorized group access, spoofed trigger events, QR invitation replay/expiry, login brute force, duplicate events, cross-user data exposure, notification abuse — all are addressed by the RLS/RPC/Edge-Function boundaries above and should be covered by integration tests against a staging Supabase project.
