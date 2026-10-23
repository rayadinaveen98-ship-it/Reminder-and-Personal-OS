# Android V0.3e — Scheduling Reliability QA

**Date:** October 23, 2026
**Package:** `dev.reminderos.alpha.v03e` (separate development install)
**Stage:** Android debug APK and 10 Android emulator SQLite tests passed in CI; physical-device alarm timing unverified.

## Problem found in V0.3d

The local SQLite scheduling outbox could accumulate redundant REGISTER operations:
- Opening the app or running manual repair could re-enqueue future reminders even if the native registration had already been accepted.
- Consecutive device-restart recovery broadcasts could enqueue identical jobs before the scheduler processed them.
- These extra jobs made debugging harder and wasted resources. An Android PendingIntent with an identical native identity usually replaces an existing alarm, but we must not rely on that alone for database correctness.

A second issue: when a whole recurring routine was paused/archived, the UI dismissed notifications only for the one or two upcoming occurrences currently shown, not all underlying occurrences in its history.

## Fixes

1. **Deduplicated scheduling outbox:** `ensureRegisterJob` checks the occurrence ID, revision, due time and register state inside a SQLite transaction.
2. **Normal resume:** keeps previously APPLIED registrations intact and retries queued PENDING/FAILED work without creating duplicates. It also checks for missing registration requests.
3. **Reboot restoration:** can re-queue accepted alerts Android cleared, but only once while a queued retry exists. It does not delete the original reminder or change user outcome.
4. **Explicit repair:** can force a registration check even when SQLite says an alarm was applied, if the user suspects Android dropped it; simultaneous queued requests remain deduplicated.
5. **Routine Pause/Archive:** dismisses every currently pending occurrence's notification, not just the small subset rendered on the screen.
6. **Scheduling health readout:** shows upcoming active, unresolved overdue, queued and failed scheduling job counts. These are diagnostics, **not confirmed notification delivery**.

## Android emulator tests added

Seven actual Android SQLite tests in `ReminderRecoveryInstrumentedTest.kt`:
- Reopening repeatedly doesn't duplicate a pending register job.
- Applied native registrations are respected unless explicitly repaired.
- Repeated reboot restoration enqueues one retry.
- Failed registration stays retryable without duplicate jobs.
- Old action revision cannot complete an already-snoozed occurrence.
- Paused/archived reminder cannot be re-registered by a stale job.
- Daily series has independently resolved/unknown occurrences and pausing all remaining stops their eligibility.

Prior backup restore emulator tests are retained. These tests validate **database state and queuing**, not a delivered system notification or reboot event on hardware.

## Known limits

- A process can still die between a successful OS alarm registration and the SQLite APPLIED update. Replaying the deterministic native PendingIntent minimizes duplicate OS registrations but is not a distributed atomic transaction.
- App force-stop, power-off and OEM battery/Doze policies can prevent prompt delivery.
- Exact-alarm eligibility and notification permission may change outside the app; the Repair action is available, but there is no 24/7 background monitor.
- For very large numbers of reminders, Android has scheduling and battery constraints; the app still has a 250-job scheduling batch limit and needs stress testing.
- Only one currently supported mobile reminder app is the Android developer alpha; iOS remains a limited simulator notification preview, not feature-parity.
- V0.3e installs separately to protect older alpha package data. V0.3c/V0.3d encrypted `.rpos` files can be checked and imported into an empty V0.3e installation, but **no automatic cross-package migration** is provided.
- A stable signed update APK requires owner-configured signing secrets on a reviewed trusted branch; the release workflow alone does not supply signing credentials.

## Device QA

1. Install V0.3e without removing V0.3d or older alpha packages; don't expect auto-import.
2. Schedule two future one-time reminders, close and reopen the app repeatedly. Scheduler pending job count should not keep growing.
3. Use Repair twice before a reminder fires; verify you don't receive duplicate notifications.
4. Snooze one reminder, then try an old notification Done action: it must not complete the new snoozed occurrence.
5. Schedule a daily series, pause it and inspect whether previously posted notifications disappear; resume and check future alerts.
6. Restore an existing V0.3c/V0.3d encrypted backup on a separate clean V0.3e installation; verify no records or due dates are corrupted.
7. Reboot with future alerts, inspect delays, permission/exact-alarm mode, and note OEM battery restrictions.
8. Never upload real reminder titles, passwords or encrypted backup files to the public GitHub issue tracker.

## Next milestone

After emulator/build validation, move toward an actual feature-parity iOS app and stable signing/upgrade process. The larger Personal OS scope—goals, routines, tracked metrics and explainable insights—remains to be implemented.

## Verified V0.3e CI and APK — 2026-10-23
- [Android emulator recovery QA #39903891862](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39903891862): **10/10 Android 15 (API 35) instrumentation tests passed** (3 earlier encrypted backup/recovery tests and 7 new scheduling idempotency/recurrence/obsolete-action tests).
- [Standard CI #39903891795](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39903891795): all 4 jobs passed: Android debug APK/JUnit, portable SQLite contract, shared JVM, iOS Simulator preview.
- Android artifact `android-reminder-v03e-debug` from source revision `7b7812ac2a6c34a487ae56a2963632ba65999be3`. Extracted APK **940,398 bytes**, SHA-256 `b9df0634de2688534607601595753930fe76db3f6deccf1d3ed258f683751270`. Archive integrity and binary manifest/classes verified.
- Debug APK package: `dev.reminderos.alpha.v03e`, Android 8.0+ minimum. This intentionally does not update the previous app package or import data automatically.
- Stable release signing remains **not configured**. Physical-device Doze, exact alarm permission, restart and notification delivery still need testing. Internal SQLite consistency does not prove a person saw or completed a reminder.
