# Trigger Connect

A production-grade, native **Android (Kotlin / Jetpack Compose)** app optimized for Samsung Galaxy / One UI. Register with just a display name, get a secret six-digit login code, connect privately via QR, form groups, and press one big **TRIGGER** button to push a custom message + notification to a person or a whole group.

- **Client:** Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt, Coroutines/StateFlow, CameraX + ML Kit (QR), Room cache, Android Keystore-backed session storage.
- **Backend (free):** **Supabase** — Postgres + Row-Level Security + Edge Functions (Deno/TypeScript) + Auth. Chosen over Firebase Cloud Functions because it has a genuinely free tier (Cloud Functions requires the paid Blaze plan).
- **Push:** **Firebase Cloud Messaging** for delivery only — FCM is free on the Spark plan; no Blaze required.

> **App identity is configurable in one place** — edit `APP_NAME`, `APP_APPLICATION_ID`, `APP_VERSION_NAME`, `APP_VERSION_CODE` in [`gradle.properties`](gradle.properties). Defaults: *Trigger Connect*, `com.triggerconnect.app`, `1.0.0`.

---

## 📲 Download & install (v1.0.4)

**➡️ [Download the APK](https://github.com/enesikaja/TriggerConnect-APK/raw/main/download/trigger-connect.apk)** (~46 MB) · [SHA-256](download/trigger-connect.apk.sha256)

Install on an Android phone (tested on Samsung):
1. Open that link in the phone's browser and download the APK.
2. When prompted, allow **"Install unknown apps"** for your browser/file manager.
3. Open the downloaded file and install, then launch **Trigger Connect**.
4. Tap **Create Account**, enter a name → you get a 6-digit code. Do this on both phones (two names/codes).
5. On phone A open **People ▸ My QR**; on phone B open **Scan** and scan it → accept the request. Now either phone can pick the other and press **TRIGGER** — the other phone gets a push notification.

This debug build is wired to the live backend and FCM, so it works immediately.

---

## ✅ Live backend already provisioned

A free Supabase backend for this app is **already deployed and smoke-tested**:

- **Project URL:** `https://wthkisizvvfltumdspcl.supabase.co` (ref `wthkisizvvfltumdspcl`, region eu-west-1)
- The live backend runs all 13 Edge Functions. Apply every migration through `0010` when deploying this source to another project.
- Verified end-to-end: register → login → session mint → authorized RPC succeed; invalid codes return 401; `anon` is denied on internal RPCs.
- [`local.properties`](local.properties) is already pointed at it, so the Android app will talk to this backend as soon as you build.
- **Still your step:** add your own Firebase `google-services.json` + `FCM_SERVICE_ACCOUNT` to enable **push delivery** (triggers, groups, and history work without it — only the push itself is skipped). Manage the Supabase project at <https://supabase.com/dashboard/project/wthkisizvvfltumdspcl>.

> Configure `CODE_HASH_SECRET` and `INVITE_HASH_SECRET` as Edge secrets before deploying. QR and login secrets never belong in source control.

## ⚠️ Important: what is and isn't done for you

This repository contains the complete app and backend source. Release APKs are published in the repository's GitHub Releases page after the checks below pass.

---

## Repository layout

```
app/                     Android application (Kotlin/Compose)
  src/main/java/com/triggerconnect/app/
    core/                Result type, dispatchers, network monitor, time format, constants
    data/                remote (Supabase), local (Room + Keystore), repositories, mappers
    domain/              models + repository interfaces
    di/                  Hilt modules
    messaging/           FCM service, notification channels + helper
    ui/                  theme, navigation, components, 18 screens + ViewModels
  src/test/              JVM unit tests
  src/androidTest/       Room + Compose instrumented tests
supabase/
  migrations/            Database schema, RLS, RPCs, and group limits
  functions/             13 Edge Functions + _shared helpers (auth, FCM, crypto, http)
  config.toml            CLI config (verify_jwt per function)
.github/workflows/       android-ci.yml (build+test) · android-release.yml (sign+checksum+release)
docs/                    BACKEND_SETUP · SECURITY · BUILD_AND_RELEASE · ARCHITECTURE
```

## Prerequisites

- **Android Studio** (Ladybug or newer) with JDK 17 and Android SDK 35.
- A free **Supabase** account + the [Supabase CLI](https://supabase.com/docs/guides/cli).
- A free **Firebase** project (for FCM only).
- For release signing: a keystore you generate (`keytool`).

## Quick start (5 steps)

1. **Open in Android Studio.** `File ▸ Open` this folder. Android Studio downloads Gradle and generates the Gradle wrapper. (CLI alternative: `gradle wrapper --gradle-version 8.11.1`.)
2. **Stand up the backend** — see [docs/BACKEND_SETUP.md](docs/BACKEND_SETUP.md). In short:
   ```bash
   supabase link --project-ref <your-ref>
   supabase db push                       # applies supabase/migrations/*
   supabase secrets set --env-file supabase/functions/.env   # CODE_HASH_SECRET, INVITE_HASH_SECRET, FCM_SERVICE_ACCOUNT
   supabase functions deploy              # deploys all 13 functions
   ```
   This deployment step is required after pulling source updates; an APK update cannot change server-side functions or database migrations by itself.
3. **Configure FCM** — create a Firebase project, add an Android app with package `com.triggerconnect.app`, download `google-services.json` into `app/`, and put the Firebase **service-account JSON** into the `FCM_SERVICE_ACCOUNT` Edge secret (see backend doc).
4. **Point the app at Supabase** — create `local.properties` in the project root:
   ```properties
   SUPABASE_URL=https://YOUR-PROJECT.supabase.co
   SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
   ```
5. **Build & run** on a Samsung device (USB debugging on):
   ```bash
   ./gradlew installDebug
   ```

## Testing

```bash
./gradlew testDebugUnitTest         # JVM unit tests (no device)
./gradlew connectedDebugAndroidTest # instrumented tests (device/emulator; validate on a real Samsung)
./gradlew lintDebug
```

## Release APK (signed) + real download link

Two ways — details in [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md):

- **Locally:** create a keystore + `keystore.properties`, then `./gradlew assembleRelease`. Output: `app/build/outputs/apk/release/`. Verify with `apksigner verify` and `sha256sum`.
- **CI (recommended):** push a tag `v1.0.0`. The [`android-release`](.github/workflows/android-release.yml) workflow runs tests, builds a **signed** APK, verifies the signature, generates a **SHA-256**, and publishes a **GitHub Release** — whose asset URL is the real HTTPS download link. Optional Firebase App Distribution for private beta.

Required CI secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `GOOGLE_SERVICES_JSON` (base64), `SUPABASE_URL`, `SUPABASE_ANON_KEY` (+ optional `FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT`).

## Security highlights

Server-side enforcement throughout: six-digit codes are generated with a CSPRNG and stored only as a **keyed HMAC** (never plaintext); login is **rate-limited** per IP with lockout; all privileged writes go through **Edge Functions** (service role) and all reads through **RLS + SECURITY DEFINER RPCs**; QR payloads are **opaque, signed, expiring** tokens (never credentials); triggers are **idempotent**; FCM is sent **only from the backend** (no FCM server key in the APK); no secrets are committed. See [docs/SECURITY.md](docs/SECURITY.md).

Honest delivery semantics: the app reports "accepted by push provider" vs "acknowledged by recipient" and never claims guaranteed device delivery (Android battery/force-stop limits apply).

## Known limitations / notes

- The Supabase Kotlin SDK calls target **supabase-kt v3.0.x**. If you bump the SDK and an API changed, the only file to touch is [`SupabaseApi.kt`](app/src/main/java/com/triggerconnect/app/data/remote/SupabaseApi.kt) (plus `AppModule` client setup).
- `google-services.json` is optional for debug builds — without it the app builds and runs but push won't initialize.
- Validate the signed APK on a **physical Samsung** device, not just an emulator.

## Acceptance-criteria mapping

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#acceptance-criteria) for a point-by-point map from the spec's final acceptance criteria to the code that implements each.
