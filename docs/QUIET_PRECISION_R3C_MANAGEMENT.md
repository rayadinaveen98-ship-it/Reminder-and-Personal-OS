# Quiet Precision R3c — Native Schedule Management and Reliability Settings

**Date:** 2026-10-24  
**Status:** Source implementation ready for automated CI, not a signed/stable release.  
**Branch:** `feature/quiet-precision-reminder-management-r3c` (stacked on R3b), **Android alpha versionCode 13 / 0.6.2-r3c-alpha**.

## Goal
Replace the next group of old test-utility controls with consistent Quiet Precision screens, while retaining the existing SQLite/JSON persistence, native reminder scheduling, and encrypted backup implementation.

## Actual changes

**Android**
- Adds `QuietPrecisionManagementActivity` as an explicitly non-exported activity with Reminders/Settings pages.
- Live recurrence list from `ReminderDatabase.rules()`; Pause/Resume calls `setRoutinePaused`, archives call `archiveRoutine`, and History reads `routineHistory`. Native PendingIntent cancellation, outbox flush and recurrence reconciliation remain in original helpers.
- One-time reminders show actual status and history, allow pause/resume via the revision-guarded `setPaused` and confirmation-gated archive via `archive`.
- Settings: actual `ReminderAlarmEngine.capabilityText`, runtime permission request when necessary, link to exact-alarm settings when eligible, manual `reconcileFuture(force=true)` and `flush()`, DB-backed pending/failed jobs, future and overdue counts.
- Backup entry explicitly routes to original `MainActivity` where existing legacy RPOSB1 and portable RPOSB2 export/verify code remains. **RPOSB2 import is not yet available.**
- Home and Library route to the new management activity, while original recovery tools remain accessible as fallback; neither ID nor database schema changes.

**iOS**
- Adds `QuietPrecisionManagementView` as the native Reminders/Settings sheet.
- Displays `IOSReminderStore.activeRules` with calendar recurrence summaries; Pause/Resume calls `setRoutinePaused`, confirmation archive calls `archiveRoutine`; history uses stored occurrence statuses.
- One-time reminder history opens the already implemented detail sheet, with confirmation archive through existing store.
- Settings reads `permissionStatus`, refreshes/request authorization, provides iOS app-settings deep link, and offers existing `reconcile()`.
- Backup action enters the preserved `PreviewContentView` exporter/verifier. iOS Focus and rolling pending-notification limits are stated; it does not claim guaranteed delivery.
- No iOS bundle ID, local persistence schema or notification delegate/handlers changed.

## Built-in safeguards

1. User-triggered archive requires confirmation; historical outcomes remain stored according to existing native domain behavior.
2. The app never converts unacknowledged reminders into Done or Skip.
3. All mutation routes rely on existing reminder engines, not UI-level duplicate state.
4. No sign-in, cloud upload, background observer, AI-based scheduling, import or migration introduced.
5. Native debug builds are not compatible signed releases by default; do **not** uninstall an existing alpha to test R3c if stored records need preservation.

## Non-destructive UI smoke tests added

- Android `QuietPrecisionManagementInstrumentedTest` enters Settings and Reminders and checks the factual backup limitations.
- iOS `QuietPrecisionManagementViewTests` hosts the screen without changing stored events.
- Retains R3/R3b and domain tests; GitHub CI must validate all compilation and emulator/XCTest suites.

## Pending items (NOT implemented in R3c)

- Safe in-place **editing of existing recurring rules' time and weekday recurrence**; this needs versioned rule revisions and atomic reconciliation with original occurrence history.
- Full custom backup/export UI replacement; the protected exporter is deliberately kept in existing tools instead of copied into a new screen.
- V0.5e portable RPOSB2 **import and rollback**, required before data migration or new store identity.
- Full archive browser, rich notification customization/quiet hours, goals, trackers, weekly evidence-linked insights, first-launch branding/animations, screen reader and physical-device release QA.
- Real-device screenshot/visual QA and production signature/update-path validation.

## R3c acceptance and next steps

This milestone is **build-verified** only after GitHub CI and Android emulator runs pass. Native **reliability validated** only after physical Android/iPhone test matrix. Next engineering slices should implement safe recurring-rule edit semantics in the data layer, followed by true archive/history UX and V0.5e import before any storage rewrite.
