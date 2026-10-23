# Shared Recurrence Parity Contract — V0.5a

**Date:** 2026-10-23
**Branch:** feature/shared-recurrence-parity-contract
**Status:** Verified: Kotlin common tests, Android native JUnit, 20 iOS Simulator XCTest, SQLite contracts and all four GitHub CI jobs passed.

## Context

Android V0.3e uses `java.util.Calendar` and a local SQLiteOpenHelper store. iOS V0.4b uses `Foundation.Calendar` and an atomic local JSON store. The already-existing KMP `Recurrence.kt` implements platform-independent Gregorian dates/slots, but its complete semantics were not yet represented as a named contract against both native apps.

This phase introduces a **shared source of truth for recurrence semantics and compatibility tests**, *not* production cross-platform persistence or actual iOS<->Kotlin bridging. Both apps remain native implementations, and we must not claim the native algorithms were replaced.

## Portable contract

`shared/src/commonMain/kotlin/dev/reminderos/core/PortableRecurrenceContract.kt` provides:
- `PortableRecurrenceSpec`: validated cadence ONCE / DAILY / WEEKLY / MONTHLY, start local date, wall-clock policy, series ID, ISO weekday mask.
- Weekday positions: **Monday bit 0**, Tuesday bit 1, … Sunday bit 6. A weekly mask must be nonzero. A nonweekly mask must be zero.
- `portableOccurrenceKeys`: bounded list of **(series ID, ISO local calendar date)** identity keys. No epoch timestamp appears in the identity; changing notification delivery time/snoozing must not create a second logical occurrence.
- Monthly rules repeat on the anchor day; months lacking it are skipped. Leap-year February 29 follows the Gregorian calendar.
- Native adapters still decide which epoch instant matches each slot's local wall clock. DST gaps/folds, moving time zones and exact-alarm permissions must be tested on each platform independently.
- `PortableActionGate` validates per-occurrence revision and UNKNOWN status before accepting Done/Skip/Snooze actions; no notification receipt is considered an outcome.

## Cross-platform test vectors

All three suites share the exact following UTC local calendar cases:

| Cadence | Anchor date | ISO weekday mask | End | Expected local dates |
|---|---|---:|---|---|
| Daily | 2026-10-23 | 0 | 2026-10-26 | Oct 23, 24, 25, 26 |
| Weekly Mon/Wed/Fri | 2026-10-23 | 21 | 2026-10-30 | Oct 23, 26, 28, 30 |
| Weekly Tue/Thu | 2026-10-26 | 10 | 2026-11-03 | Oct 27, 29, Nov 3 |
| Monthly on 31st | 2026-01-31 | 0 | 2026-04-30 | Jan 31, Mar 31 |
| Monthly on 29th | 2024-01-29 | 0 | 2024-03-31 | Jan 29, Feb 29, Mar 29 |

Tests:
- Kotlin Multiplatform: `PortableRecurrenceContractTest.kt` (10 cases) via Gradle `shared:jvmTest` and iOS KMP compile.
- Android JVM/JUnit: `CrossPlatformRecurrenceParityTest.kt` (5 cases) in `androidApp` testDebugUnitTest.
- iOS Swift/XCTest: `IOSCrossPlatformParityTests.swift` (5 cases) included in iOS simulator test workflow.
- Existing suites continue testing wall-clock DST behaviour, native iOS V1→V2 store compatibility and SQLite outbox reliability.

## What this milestone does **not** accomplish

- Neither native app has yet been rewired to invoke the KMP recurrence source at runtime.
- No KMP multiplatform SQLDelight/Room persistence integration; no migration from Android SQLite/iOS JSON stores.
- No portable import/export or stable shared reminder IDs across two *different* installed apps.
- No automatic sync, iPhone signed IPA, continuous iOS background scheduling, or guaranteed native notification delivery.
- A fixed seven-upcoming-occurrence iOS window cannot guarantee perpetual unattended reminders without app execution.
- Production user-level Personal OS analytics, goals and trackers are future phases.

## Next implementation milestone

1. Produce an installable KMP framework + platform adapters that actually call `PortableRecurrenceSpec` on both platforms.
2. Implement a versioned, durable portable storage schema and migration adapters for the existing Android SQLite and iOS JSON data without destructive resets.
3. Add twin-device fixture tests that compare the same encoded reminder/recurrence operations and resulting state.
4. Sign releases securely and conduct iOS/Android real-device notifications + DST/travel QA.

## Verified CI result — October 23, 2026
- [GitHub Actions run #39936693872](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39936693872): **all 4 jobs passed** (shared JVM, Android native app/Gradle/JUnit, iOS Simulator/KMP/XCTest, portable SQLite contracts).
- iOS Simulator XCTest: **20/20 tests passed** — 10 existing recurrence tests, 5 existing persistence tests and 5 new cross-platform parity tests.
- Android `testDebugUnitTest assembleDebug` and shared Kotlin `jvmTest` both passed, including the new 5 native Android parity vectors and 10 common contract tests.
- Source tested from head `53ae18fab2acac48af7bb0b4e862fcbea975c5e8`. Later documentation-only commits do not change executable code.
- No new user-visible app feature or signed installer is produced. Android V0.3e runtime and iOS V0.4b native storage remain unchanged; no physical-device notification proof is claimed.
