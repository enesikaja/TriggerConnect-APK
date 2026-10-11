# Architecture

## Layers (Clean Architecture + MVVM)

```
ui (Compose screens + ViewModels)   ← StateFlow / one-shot Channels
      │ depends on
domain (models + repository interfaces)   ← pure Kotlin, no Android
      │ implemented by
data (repositories)
   ├── remote  → SupabaseApi (Edge Functions + RPCs), SecureSessionManager
   └── local   → Room (cache, single source of truth for lists), EncryptedSharedPreferences
```

- **Single source of truth:** list screens observe Room `Flow`s; repositories refresh from the network (RPCs) and write into Room. This keeps the UI responsive and gives offline cached data.
- **DI:** Hilt. `AppModule` builds the Supabase client, Room DB, secure storage, network monitor, JSON, app scope; `RepositoryModule` binds repositories.
- **Networking choke point:** every Supabase call goes through [`SupabaseApi`](../app/src/main/java/com/triggerconnect/app/data/remote/SupabaseApi.kt), which maps transport/HTTP errors to a typed `AppError`. Privileged writes → Edge Functions; reads → `postgrest.rpc(...)`; owner-scoped saved-message writes → direct PostgREST under RLS.
- **Auth:** `AuthRepositoryImpl` turns the six-digit code into a real GoTrue session and exposes `authState` that drives top-level navigation.
- **Push:** `TriggerMessagingService` (FCM) renders **data** messages in all app states and deduplicates by `eventId`; `FcmTokenRegistrar` registers the token after sign-in.

## Backend

- **Postgres** schema with explicit indexes (`supabase/migrations/0001_init.sql`).
- **RLS** locks down direct table access; **SECURITY DEFINER RPCs** serve authorized reads (`0002`, `0003`).
- **Edge Functions** (Deno/TS) perform all privileged writes with the service role and enforce authorization, rate limiting, idempotency, invitation validity, and FCM dispatch.

## Navigation

A single `NavHost` ([`AppRoot`](../app/src/main/java/com/triggerconnect/app/ui/navigation/AppRoot.kt)) with an auth graph (welcome/code/login) and a `main` graph. The five tabs share one activity-graph-scoped `TriggerComposerViewModel`, so a recipient picked on People/Groups flows to the Home TRIGGER button. A bottom bar shows only on tab routes; sign-out returns to Welcome.

## The 18 required screens

| # | Screen | File |
|---|--------|------|
| 1 | Splash | Android 12 splash via `Theme.TriggerConnect.Starting` + `installSplashScreen()` |
| 2 | Welcome / Registration | `ui/screens/welcome/WelcomeScreen.kt` |
| 3 | Six-Digit Code Display | `ui/screens/code/CodeScreen.kt` |
| 4 | Login | `ui/screens/login/LoginScreen.kt` |
| 5 | Home Dashboard | `ui/screens/home/HomeScreen.kt` |
| 6 | Direct Connections | `ui/screens/people/PeopleScreen.kt` |
| 7 | Personal QR Code | `ui/screens/qr/PersonalQrScreen.kt` |
| 8 | QR Scanner | `ui/screens/scan/ScanScreen.kt` |
| 9 | Group List | `ui/screens/groups/GroupsScreen.kt` |
| 10 | Create Group | `ui/screens/groups/CreateGroupScreen.kt` |
| 11 | Group Details | `ui/screens/groups/GroupDetailsScreen.kt` |
| 12 | Group Invitation QR | `ui/screens/groups/GroupInviteScreen.kt` |
| 13 | Create/Edit Trigger Message | `ui/screens/message/MessageEditorScreen.kt` |
| 14 | Trigger Confirmation | urgent-confirm dialog in `HomeScreen.kt` |
| 15 | Trigger Details | `ui/screens/trigger/TriggerDetailsScreen.kt` |
| 16 | Trigger History | `ui/screens/history/HistoryScreen.kt` |
| 17 | Notifications | `ui/screens/notifications/NotificationsScreen.kt` |
| 18 | Settings | `ui/screens/settings/SettingsScreen.kt` |

## Acceptance criteria

| Criterion | Where |
|-----------|-------|
| Register with only a display name | `WelcomeViewModel` → `auth-register` |
| Six-digit code generated securely | `_shared/crypto.ts` `randomSixDigitCode` + HMAC store in `auth-register` |
| Log in using that code | `LoginViewModel` → `auth-login` (rate-limited) |
| Connect with another person via QR | `PersonalQrScreen` / `ScanScreen` → `invite-resolve` + `connection-request` + `connection-respond` |
| Create & join groups via QR | `GroupInviteScreen` → `group-invite-create`; `ScanScreen` → `group-join` |
| Create a custom message | `MessageEditorScreen` + `saved_messages` (RLS) |
| Select one person or a group | `TriggerComposerViewModel` + People/Groups pickers |
| Pressing TRIGGER creates a real backend event | `trigger-dispatch` (immutable `trigger_events`) |
| Recipients receive push notifications | `_shared/fcm.ts` (HTTP v1) → `TriggerMessagingService` |
| Events appear in history | `HistoryScreen` ← `my_history` RPC |
| Users can acknowledge received events | `TriggerDetailsScreen` → `event-acknowledge` |
| Authorization & security tests | `docs/SECURITY.md` + `src/test` + RLS/RPC/function boundaries |
| Release APK signed | `.github/workflows/android-release.yml` |
| Installs/runs on a Samsung phone | `installDebug` / release APK + `connectedAndroidTest` |
| Valid HTTPS download link after publish | GitHub Release asset created by the release workflow |
| Idempotent / no duplicate sends | `clientEventId` + `unique(sender_id, client_event_id)` |
| Offline never claims delivery | `NetworkMonitor` gate in `TriggerComposerViewModel` + `OfflineBanner` |
| Account deletion | `SettingsScreen` → `account-delete` (cascading delete) |
