# Trigger Connect — implementation status

Updated 9 October 2026 against the supplied master development prompt.

## Implemented in the Android project

- Native Kotlin, Jetpack Compose, Material 3, MVVM/Clean Architecture, Hilt, Room, CameraX/ML Kit QR scanning, encrypted Keystore-backed session storage, and Gradle Kotlin DSL.
- Display-name registration, one-time six-digit code display, code login, persistent session, explicit logout, and account deletion.
- Personal QR connections, requests, accept/reject, disconnect/block, group creation, expiring/max-use group QR invitations, invitation rotation/revocation, member roles, member removal, approval/rejection, leave, rename, and delete.
- Custom and saved messages, including create, select, edit, and delete; 280-character validation.
- Direct/group trigger dispatch, server authorization, idempotency, anti-spam limit, sender exclusion/self-notify option, priority, history filters, group history, event details, and acknowledgment.
- FCM data messages, foreground/background receiver, notification deep link, permission request on Android 13+, deduplication by event ID, priority channels, sound/vibration preferences, token refresh, token cleanup, logout unregistration, bounded retry, and provider-acceptance wording.
- Offline indicator and disabled dispatch while offline; local cache is cleared at logout so another account cannot see stale data.
- Default recipient/group preferences, Android notification settings shortcut, system dark mode, accessible labels, and adaptive Compose layout.
- Debug/release CI, required-secret validation, unit tests, lint, signing, signature verification, SHA-256 generation, GitHub Release publishing, and optional Firebase App Distribution.

## Backend deployment required

The source now includes migrations `0005`–`0009` and revised Edge Functions. They add device-aware login abuse controls, pair uniqueness for connections, group approval data, atomic invitation consumption, hashed/rotating personal invitations, trigger throttling, safe push failures/retries, restricted connection writes, and checked database writes. These changes must be deployed to the configured Supabase project before the revised behavior is live.

## External acceptance items

These items cannot be truthfully marked complete from source code alone:

- A stable release signing key must be created/owned by the publisher and stored as GitHub Actions secrets.
- A GitHub repository and its URL are required before the workflow can publish an HTTPS APK link.
- The FCM service-account secret must be present in Supabase.
- End-to-end push delivery needs two authenticated test accounts/devices.
- Final acceptance requires installation and notification testing on a physical compatible Samsung phone; an emulator cannot validate Samsung One UI battery and notification behavior.

The project must not be described as fully released until those external checks pass.
