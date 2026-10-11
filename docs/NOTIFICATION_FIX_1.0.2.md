# Notification fix — 1.0.2

## Changes

- A bundled six-second alert and longer vibration pulses on all trigger channels.
- Versioned channels upgrade existing installations while retaining explicit Android sound/importance choices and disabled vibration.
- Sound and vibration remain independent; vibration-only notifications are no longer silenced by `setSilent`.
- FCM displays the notification before its callback returns. Network history refresh is separate.
- Device-token registration retries when returning to the app, including after offline sign-in.
- Settings includes a local ringtone/vibration test with blocked-notification feedback.
- The default Firebase channel now names a channel that actually exists.
- Repeated delivery of the same event updates its notification without sounding again.
- The ringtone is explicitly retained during release resource shrinking.

## Backend

The live `trigger-dispatch` function was updated through the Supabase dashboard on 2026-10-10:

- Recipient rows are inserted before sending FCM, so an immediate recipient history read can see the event.
- Delivery status is updated on those existing rows without overwriting acknowledgments.
- Push configuration/network errors become failed delivery statuses instead of leaving the event dispatching.
- Existing high-priority delivery is retained; TTL is five minutes for urgent triggers and one hour otherwise.

The production function was older than the repository. Only the notification fixes above were applied to production, preserving its other behavior. Local source additionally contains earlier changes not published as part of this task. Exact before/deployed snapshots are in `build/server-notification-fix/`; deployment evidence is `dist/server-notification-fix.jpg`.

The FCM secret's digest matches the configured local service-account file, and its Firebase project matches the Android configuration. An unauthenticated request to the deployed function returns HTTP 401 as expected.

## Verification and device checks

The app compiles, all 19 JVM tests pass, and Android lint reports zero errors (pre-existing warnings remain). APK signing is verified with apksigner. Android notification regression tests check every sound/vibration combination and duplicate notification replacement.

Install `dist/TriggerConnect-v1.0.2-test.apk` on both phones as an update, then open the app and allow notifications. Use Settings → Test ringtone and vibration. Test a trigger with the recipient app open, backgrounded, and the screen locked.

Physical Samsung loudness/vibration and end-to-end delivery between two physical phones have not been verified here. Notification volume, vibration intensity, Do Not Disturb, and Sleeping apps are controlled by the recipient's phone; the app does not override them. Force-stopped apps must be reopened.
