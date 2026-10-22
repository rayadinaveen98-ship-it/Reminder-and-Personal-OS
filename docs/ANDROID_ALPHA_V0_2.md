# Android Alpha v0.2 — installable test build

**Date:** 2026-10-22  
**Build status:** Android debug APK compiled successfully in [GitHub Actions run #39819474781](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39819474781).  
**Physical-device scheduling reliability:** NOT TESTED.  
**Release level:** Developer alpha, not an approved stable release.

## Artifact

- CI artifact: `android-reminder-alpha-debug` (retention 7 days).
- Built from code commit `9d1c9dad5eaee00b74142365bbd03d3fa6c5628d`.
- APK filename in CI: `ReminderAndroidPreview-debug.apk` (the Gradle project still has its previous name).
- Package/application ID: `dev.reminderos.alpha`.
- Version: `0.2.0-alpha`, versionCode 2, min SDK 26.
- Locally extracted APK SHA-256: `e404e0ad58bb83f696d9e89de0f711194c960286f4c9275be7c3aedf5cb643ab`.
- CI also passed 25 SQLite contract tests, shared JVM tests, and iOS simulator preview build. The iOS app remains the prior single-alert preview; it does **not** have these Android alpha features.

## What is implemented

- Multiple one-time reminders with a text title and date/time picker.
- Local persistent SQLite store for reminder definitions, due times, original due times, outcome states, action/audit history, and durable pending schedule-operation jobs.
- Native per-reminder Android AlarmManager scheduling with stable `PendingIntent` URI identity.
- Exact scheduling when eligible; fallback to inexact scheduling when exact-alarm permission is not granted.
- Notification buttons: Done, Snooze 10m, Skip.
- Revision guard rejects late native action callbacks for a previous snooze state.
- App list shows pending, overdue-unresolved, done and skipped reminders.
- Reconcile stored future scheduling intentions on supported boot/time/timezone/package update callbacks.
- Unknown/no response never silently becomes Done or Skipped.

## Important limitations

- **Android only** for this alpha. iOS advanced reminders, shared persistence and app-level tracking/analytics are not yet integrated.
- Only **one-time** reminders. Recurrence, goals, automated insights, attachments, health integrations, search, full analysis and advanced UI are pending.
- An OS scheduler callback is not confirmation that the user saw the alert or completed the task.
- If notification permission is denied, future reminders are saved but cannot display visible notifications until permission is granted and scheduling is retried.
- Android force-stop, power-off, OS restrictions and OEM battery policies may prevent or delay notifications; do not use this as an emergency or safety-critical reminder.
- The alpha keeps data only in app-private local SQLite; **no export/sync/backup**. Uninstalling it loses its stored reminders.
- This is a separate package from the 60-second preview, so installation does not overwrite that preview. CI debug signing can differ across builds; future alpha upgrades may require a reproducible signing key.
- The Android SQLiteOpenHelper schema is a small native alpha implementation, **not** yet the shared KMP schema or a production Room implementation. Robust migration, encrypted backup, and platform parity are still required.
- The new data store is not automatically migrated from the one-alert preview. This is a feature prototype, not a production-grade release.
- No physical-device UI or alarm scheduling tests have yet been executed by the development environment.

## Suggested real-device QA

1. Install the APK, grant notification permission, inspect exact-alarm eligibility.
2. Create a test reminder two or three minutes ahead. Note the scheduled time, lock the phone and observe actual notification receipt time.
3. Tap Done in notification; reopen app and verify the outcome survives a normal app restart.
4. Set another test reminder and tap Snooze 10m; verify the next notification is not duplicated.
5. Set a reminder and tap Skip; confirm no obsolete alarm appears.
6. Test cancellation/snooze via in-app buttons without a notification.
7. Set a reminder for several minutes ahead, reboot the device and check if the future reminder is restored.
8. Test notification permission denial and grant, approximate scheduling with exact-alarm access denied, and device battery restriction modes.

Report failures with device model, Android version, permission states, reminder scheduled time, observed callback time and screenshots if possible. **Never upload actual private reminder titles or notes to the public repository.**

## Next gate

Integrate Android native persistence with the shared Kotlin domain and properly tested migrations; perform device-level reliability QA; build equivalent iOS persistent reminder application. Then implement recurring schedules, richer tracking and analytics.
