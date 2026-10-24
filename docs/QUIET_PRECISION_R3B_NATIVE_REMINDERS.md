# R3b — Quiet Precision Native Reminders and History

**Date:** 2026-10-24  
**Working branch:** `feature/quiet-precision-reminder-editor-r3b` (draft PR #21), stacked on R3 PR #20.  
**Status:** Native editor and history code committed. CI started; check run before claiming alpha test pass.  
**Data safety:** No schema migration, backup import, account requirement, fake analytics, or replacement of platform notification engines.

## Why R3b exists
R3 made Quiet Precision the starting screen and connected outcomes to real user records, but creating reminders still opened the old testing form. R3b gives the primary creation experience the selected R2 design language on both platforms without changing existing durable stores.

## Android

- New `QuietPrecisionReminderEditorActivity` declared non-exported in the manifest.
- App Home quick-add and Library route into this native editor; legacy tool screen remains accessible.
- Create one-time, daily, selected-weekday or monthly reminders, with date/time selection, local persistence, validation and proper success/failure feedback.
- Edit one-time unresolved reminders with the **existing revision-guarded `ReminderDatabase.edit`**. Editing an old recurring occurrence isn't supported here; routine management remains in original tool screen.
- Save flushes the existing AlarmManager scheduling outbox after the database confirms writing. Stale revision / failure does not dismiss editor as success.
- Reminder History reads `recentEvents` from existing store; no notification callback is treated as evidence of Done.
- No change to `dev.reminderos.alpha.v05d` application ID; alpha `versionCode` now 12 (`0.6.1-r3b-alpha`). Signed in-place upgrade remains unverified.

## iOS

- New `QuietPrecisionReminderEditor` using SwiftUI Form and DatePicker, once/daily/weekly/monthly selection, named weekday toggles and a guarded save.
- `IOSReminderStore.create` / `createRecurring` retain authority for validation, JSON write and pending UNUserNotificationCenter reconciliation.
- Quick Add opens the native editor; original `PreviewContentView` still available for old management and encrypted backup export/verify.
- New `QuietPrecisionReminderDetail`: recorded status, date/slot and actual event audit history; archive one-time reminder after explicit confirmation using existing store method.
- No iOS bundle ID or persistence filename change, no iPhone-signed IPA yet.

## Added QA

- Android instrumentation smoke `QuietPrecisionEditorInstrumentedTest` launches the editor without creating/editing any test record.
- iOS XCTest `QuietPrecisionEditorViewTests` mounts editor and detail with synthetic in-memory fixture. Existing reminder/store regression tests remain.
- All four CI jobs plus Android emulator QA must pass before calling this phase build-verified.
- New Android CI artifact (if build succeeds): `android-reminder-quiet-precision-r3b-debug`; no local APK is claimed or linked until exact bytes are available.

## Next implementation focus

1. Finish remaining R3b integration checks, CI repairs and physical-device QA.
2. Move one-time edit/history and recurring management fully into new design; separate privacy/backup/diagnostics into polished screen flow.
3. V0.5e verified RPOSB2 import + atomic rollback BEFORE migrating old user data or replacing persisted stores.
4. Tracking/goal logging persistence and real 7/28/90 analytics: new engine implementation phases (not yet done).
5. Brand/icon/welcome animation native implementation after approval; all-screen polish and Android/iOS accessibility tests.

**Safety gate:** Current R3b editor may not be represented as a signed release, portable restore feature, guaranteed notification delivery or implemented Personal OS intelligence.
