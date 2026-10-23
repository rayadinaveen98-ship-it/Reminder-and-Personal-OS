# Android V0.3b — Recurring Reminder Alpha

**Stage:** Android APK and recurrence unit tests passed in CI; physical-device behaviour remains untested (developer alpha).
**Date:** 2026-10-23
**Parent:** V0.3a reminder management
**Install package:** `dev.reminderos.alpha.v03b` — separate from previous developer builds to protect their local data from signing mismatches.

## New capabilities

- Create **one-time, daily, weekly on selected weekdays, and monthly by chosen calendar date**.
- Default weekly schedule is Monday to Friday, adjustable using seven weekday checkboxes.
- Monthly date 29–31 **skips months without that date**, rather than silently changing to the month end.
- Use the anchor's local wall-clock time in the device's **current time zone** (not a guaranteed fixed zone across travel). Testing includes spring and fall DST.
- Separate recurrence rule in `recurrence_rules` and distinct `reminders` row per logical date (unique `series_id,slot_key`), retaining each occurrence's Done, Skipped or still-unknown history.
- New runs are **independent of responding** to earlier notifications. One ignored occurrence never blocks the next.
- Rolling materialization targets **seven future occurrences** per active schedule and catches up at most 90 missing slots per reconciliation transaction. It replenishes when the app opens, the Android alarm callback fires, or supported restart/recovery broadcasts arrive.
- List limits routine display to two upcoming and one most recent overdue occurrence. Routine History shows the last 25 due or explicitly resolved entries.
- Pause/Resume or Archive the *entire routine* separately from per-occurrence Done/Snooze/Skip.
- Existing one-time reminder actions, local SQLite persistence and the manual repair control remain available.

## Data migration and design discipline

V0.3b SQLite schema version 3 adds recurrence rule definitions, nullable per-occurrence `series_id` and `slot_key`, and an indexed unique logical date constraint. It can migrate **v1 → v2 → v3** or **v2 → v3**, preserving existing standalone reminders, events and scheduling jobs within the same app package.

**Important:** The V0.3b test app is **a different installed Android package** from V0.3a and V0.2 because unsigned/temporary GitHub debug signing cannot guarantee in-place upgrades. Therefore **no existing reminder data is automatically transferred between the installed apps**. Never uninstall the older app to install the new alpha if its data matters. Stable signing and safe import/export are future release requirements.

## Verification

- Android calendar math has dedicated Kotlin/JUnit tests for daily recurrence, weekdays, selected weekdays, leap-year February 29, missing monthly dates, DST spring and fall, and invalid weekday masks.
- SQLite migration contract test validates the v2→v3 ALTER/CREATE SQL extracted from the actual Kotlin implementation, preserving an old completed reminder and audit/outbox history.
- Existing v0.2→v0.3a migration and portable outbox tests remain enabled.
- GitHub Actions runs Android JUnit tests plus APK build, independent JVM/shared code, SQLite tests and iOS Simulator host checks.
- **Build success is not evidence of accurate notification delivery or Android database migration on a physical device.**

## Known limitations

1. Full recurrence history on iOS is not implemented: iOS remains a native one-shot notification preview, with shared Kotlin calendar helpers separate from this Android test harness.
2. Device local time zone can change after travel; **fixed-zone scheduling, DST gap/fold policy, and cross-zone migration** need stronger device QA.
3. Catch-up materialization is limited to 90 slots in one pass. After a very long offline interval, further passes might be needed to generate all historically missing slots.
4. Android OS restrictions (Doze, permission, OEM battery behavior, force-stop, power off) may delay or suppress alarms. There is no guaranteed emergency/medical-grade delivery.
5. A native callback records an *attempt to fire*, never proof that a person saw the notification. Unanswered occurrences remain UNKNOWN.
6. Date/time editing of **recurring series definitions** is not implemented. Pause/Resume/Archive entire series; only individual one-time reminders support Edit. Future work must reschedule the rule while retaining historical occurrences.
7. Current UI is a developer test interface. No polished design, export/backup, stable release signing, cloud sync, goals or explainable insights yet.
8. Rolling scheduling creates several outstanding Android alarms per routine; production rollout needs performance and battery/OS-behaviour testing, especially with dozens of routines.
9. SQLite transaction and Android alarm registration are distinct side effects. Stable per-occurrence OS identities and schedule outbox reduce but do not eliminate interruption races.

## Recommended device QA

- Create a daily reminder and **ignore today's occurrence**; verify tomorrow remains scheduled.
- Create Monday/Wednesday/Friday schedule; verify no extra Tuesday/Thursday alarms.
- Create monthly on 31st; verify skipped February where applicable.
- Mark one occurrence Done and another Skip; verify their distinct results in routine History.
- Snooze one occurrence for 5/10/30/60 minutes without changing a later date's occurrence.
- Pause a routine; verify future pending notifications stop. Resume and check re-registration.
- Archive a routine; check all future alarms are canceled and it vanishes from active routines.
- Restart the app and device; check scheduled future instances recover without duplicates.
- Check exact/inexact alarm permission differences and locked-screen reception.
- Record notification delay and battery conditions without exposing personal content in the public repository.

## Next steps

1. Fix CI failures and distribute the **verified** V0.3b APK for physical-device testing.
2. Sign reproducible developer builds and provide encrypted export/import so user data survives test-build upgrades.
3. Add recurrence-editing and precise per-occurrence reporting.
4. Unify native Android persistence with Kotlin Multiplatform and bring iPhone to feature parity.

## Build and test evidence — October 23, 2026
- [GitHub Actions #39897461506](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39897461506): all four jobs **PASS** — Android KMP/shared compile, native Android `testDebugUnitTest assembleDebug`, shared JVM, portable SQLite contract and iOS Simulator host compilation.
- Native Android recurrence JUnit suite: **10 test cases** in `RecurrenceScheduleTest.kt` passed as part of `testDebugUnitTest`. Includes leap years, DST spring/fall, weekly weekday masks and nonexistent monthly dates.
- SQLite schema/history/migration contract: **32 tests passed**.
- Android Actions artifact: `android-reminder-v03b-debug`, built from source revision `0ab148dc9d53762a9506f743a4a5eceb863107ca`.
- Extracted debug APK: **925,602 bytes**, SHA-256 `78de6e56c343f2fd96acfa597b43aa3c9e846cea0174f205730268d1747c58f3` (APK archive integrity checked).
- Android package `dev.reminderos.alpha.v03b`, Android 8.0+ (minSdk 26).
- This is **not** a signed release / stable app. No delivery, alarm cancellation or database upgrade has been tested on a physical Android device.
