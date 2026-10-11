# Build & release

## Debug build (development)

```bash
./gradlew installDebug      # build + install on a connected device
./gradlew assembleDebug     # just build -> app/build/outputs/apk/debug/
```

## Generate a signing keystore (once)

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias trigger-connect \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep `release.jks` and its passwords **out of git** (already gitignored). Create `keystore.properties` in the repo root for local signing:
```properties
storeFile=../release.jks
storePassword=********
keyAlias=trigger-connect
keyPassword=********
```
`app/build.gradle.kts` reads this (or the `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars in CI).

## Signed release build (local)

```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`. Then:
```bash
# verify signature
"$ANDROID_SDK_ROOT"/build-tools/*/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
# rename + checksum
cp app/build/outputs/apk/release/app-release.apk trigger-connect-v1.0.0.apk
sha256sum trigger-connect-v1.0.0.apk | tee trigger-connect-v1.0.0.apk.sha256
```

## Release via CI (recommended — produces the real download URL)

1. Add repository secrets (Settings ▸ Secrets and variables ▸ Actions):
   - `KEYSTORE_BASE64` — `base64 -w0 release.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   - `GOOGLE_SERVICES_JSON` — `base64 -w0 app/google-services.json`
   - `SUPABASE_URL`, `SUPABASE_ANON_KEY`
   - *(optional beta)* `FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT`
2. Tag and push:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
3. The [`android-release`](../.github/workflows/android-release.yml) workflow will:
   build → run unit tests → build **signed** `trigger-connect-v1.0.0.apk` → **verify signature** → generate **SHA-256** → create a **GitHub Release** with the APK + checksum attached. The release asset URL is the real, shareable HTTPS download link. (Optionally distributes to testers via Firebase App Distribution.)

> The pipeline never fabricates a URL — the link exists only after the upload step succeeds.

## Installing on a Samsung device

Share the GitHub Release link (or the raw APK over HTTPS). On the phone, allow "Install unknown apps" for the browser/file app, open the APK, install, and launch. Verify integrity first:
```bash
sha256sum -c trigger-connect-v1.0.0.apk.sha256
```

## About the Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is a binary and is not included. Opening the project in Android Studio generates it automatically; or run `gradle wrapper --gradle-version 8.11.1` once with a local Gradle install. After that, `./gradlew` works everywhere (including CI, which uses `gradle/actions/setup-gradle`).
