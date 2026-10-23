# V0.5c — Portable Storage Schema and Read-Only Migration Inspection

**Date:** October 23, 2026  
**Stage:** Android/iOS test builds and 13 Android + 23 iOS emulator/simulator tests verified passing in GitHub Actions  
**Branch:** `feature/v05c-portable-storage-snapshot`

## Why we are doing this first

Android V0.5b persists in SQLite V3; iOS V0.4b+ persists in app-private JSON V2. They do not have an interoperable backup or in-place upgrade mechanism. Running a write migrator now could silently delete archived history, treat unanswered reminders as completed, or collapse multiple recurring occurrences onto the same date.

V0.5c implements **the first phase of shared persistence**: a typed Kotlin Multiplatform canonical snapshot and **read-only, in-app migration readiness inspections** on both platforms. It does **not** yet replace Android's SQLite or iOS's JSON file, or perform any import/export/migration writes.

## Shared data contract

`PortableStorageSnapshot.kt` represents:
- Recurrence rules: stable ID, title, original anchor instant, DAILY/WEEKLY/MONTHLY, Monday-first weekday mask, paused and archived.
- Reminder occurrences: stable ID, title, original logical due instant, current due instant, revision, UNKNOWN/DONE/SKIPPED, paused, archived, optional series ID and original YYYY-MM-DD local slot.
- Immutable audit events: unique event ID, owning reminder ID, kind, revision, timestamp.
- Schema version 1; unknown versions rejected.

The validator blocks duplicate IDs, duplicate series/date pairs, invalid calendar dates, missing parent recurrence rules, orphaned audit events, invalid revisions/statuses and unsupported sizes. It **never mutates source data**.

Data semantics: Android PENDING → portable UNKNOWN; Done/Skipped preserved; iOS UNKNOWN/DONE/SKIPPED unchanged. Snoozing never rewrites the original logical date. Source-native Android `schedule_jobs`, OS alarm PendingIntents and iOS notification request identifiers **are excluded**, as they must be re-created on the destination OS.

Both legacy alpha UIs passed weekday-mask bits for some nonweekly rules. The inspectors canonicalize those masks **in memory only** to zero for daily/monthly schedules; the original SQLite/JSON remains unchanged.

## Android implementation

`AndroidPortableStorageInspector` reads ALL SQLite v3 rows under a consistent SQLite transaction, including archived entries, recurring occurrences and complete event history rather than the short on-screen reminder list.

The Android app now shows **Inspect data migration readiness** under Private backup & restore. The report includes rule/reminder/event totals, unresolved outcomes and archived counts; it does not export, alter, encrypt, delete or write data.

Added Android emulator instrumentation tests checking an archived reminder remains in migration scope, unknown/known outcome preservation, legacy weekday mask normalization, rejection of a malformed recurring slot and no changes to table/job row counts.

## iOS implementation

`IOSReminderStore.inspectMigrationReadiness()` reads the existing loaded JSON V1/V2 snapshot without changing the file, calls Kotlin/Native `StorageMigrationBridge` for per-row validation, and checks complete Swift in-memory rule/event referential integrity and recurring slot uniqueness.

The SwiftUI app exposes the same read-only inspection. Three new iOS XCTest cases cover no-write validation of a seven-occurrence daily schedule, rejection of duplicate series/day slots and protection of corrupt/unsupported files.

**Note:** Cross-record validation is currently implemented in the Kotlin batch validator for Android and in Swift for the iOS in-memory adapter. Runtime schema parity tests must continue until the iOS record collection is passed directly through a common KMP batch importer.

## What remains before safe unified persistence

1. Select and implement the actual shared database adapter, likely SQLDelight or Room KMP, with unified ID/transaction behaviour on Android and iOS; avoid a platform library that cannot run on both supported targets.
2. Define and implement a **versioned encrypted portable export container** understood by *both* platforms; Android's current `.rpos` AES-GCM format is not an iOS interoperability guarantee.
3. Add an atomic dry-run → backup → import transaction → verify counts/checksums → re-register native notifications → rollback or recover on failure flow. Ensure archived records, old user responses and incomplete outcomes survive.
4. Test conversion from **real existing** Android SQLite V3 and iOS JSON V1/V2 fixtures, including a large history, leap-day monthly rules and timezone changes.
5. Preserve existing old alpha installations; **never uninstall an older app with unique reminders to test migration**.
6. Configure stable owner-controlled signing for reproducible in-place APK updates. Physical Android/iPhone backup/restore and OS notification QA still pending.

## Verification gates

- Shared Kotlin `PortableStorageSnapshotTest` covers valid/invalid records and safe preview semantics.
- Android device-emulator tests in `PortableStorageInspectorInstrumentedTest` verify exact source SQLite table counts before and after inspection.
- iOS simulator XCTest in `IOSPortableStorageInspectionTests` verifies original JSON bytes are identical after successful/failed inspection.
- Existing Android/JVM/iOS/KMP/SQLite regression suites must remain green.

**A passing read-only preview is not a successful cross-platform migration or a promise of production reliability.**

## Verified implementation evidence — October 23, 2026
- [Four-job KMP CI #39943474845](https://github.com/rayadinave98-ship-it/Reminder-and-Personal-OS/actions/runs/39943474845): **PASS all 4 jobs** (shared Kotlin JVM tests, portable SQLite reference, Android shared/native debug APK/JUnit, and Xcode iOS Simulator framework/app/test execution).
- [Android emulator run #39943474863](https://github.com/rayadinave98-ship-it/Reminder-and-Personal-OS/actions/runs/39943474863): **13/13 instrumented Android SQLite tests passed**, including 3 new read-only storage migration inspection tests.
- Xcode iOS Simulator in the four-job run: **23/23 XCTest passed**, including 3 new JSON-preservation and duplicate-slot inspection cases; zero failures.
- GitHub debug APK artifact `android-reminder-v05c-debug` (test source commit `3378c1794ddb1fdfcfa5e475fa1d28a094b8819e`). Extracted APK **987,640 bytes**, SHA-256 `106ea142d6697cc36a2ad54c646d27b856d4ace2b6a300ef576a5ce7a33e4869`. ZIP integrity, `AndroidManifest.xml`, and `classes.dex` verified.
- The Android APK application ID is `dev.reminderos.alpha.v05c`, separate from the V0.5b package. The iOS artifact is a **simulator app** only, not a signed device IPA.
- Passing tests verify read-only inspection logic, **not** successful data conversion, interoperable encrypted export, stable production updates, physical-device notification delivery, or cloud synchronization.
