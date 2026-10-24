# Quiet Precision R3e — Full Archive & History Browser

**Date:** 2026-10-24  
**Branch:** `feature/quiet-precision-r3e-archive-history` (stacked on R3d PR #23)  
**Scope:** Native Android + iOS UI, backed by full locally stored reminders. No schema, alarm delivery engine, backup-format or app-ID changes.  
**Status:** Source and tests committed, CI verification required.

## Why this phase exists

The Android `ReminderDatabase.all()` and iOS `IOSReminderStore.visibleReminders` are **condensed home-screen projections**. They omit most recurring slots and archived records, so they cannot be authoritative for audit/history browsing. R3e adds read-only full-store access while retaining these smaller Today projections.

## Android

- New `ReminderDatabase.historyPage(archiveOnly,search,outcome,limit,offset,now)` uses SQLite **parameterized selection**, bounded 1–50 row pages, stable original-due/id ordering and explicit status filtering.
- **Archive** includes archived occurrence rows. **History** includes archived, resolved, or originally-due past instances. Future unanswered instances are excluded from History unless archived.
- Case-insensitive title search with escaped literal `%`, `_` and backslashes; duplicate due instants use stable ID tiebreak.
- New non-exported `QuietPrecisionHistoryActivity` adds History/Archive selection, search, Done/Skipped/Unresolved filters, 25-row incremental pagination and a read-only evidence detail dialog. Details re-read by persistent ID and show original and current due times plus up to 50 actual events.
- Entry from Quiet Precision Library and management. No mutation from this activity and no OS alarm registration triggered by the history view.

## iOS

- New `IOSReminderStore.historyRecords(archiveOnly,query,outcome,now)` examines **all stored occurrences**, unlike the condensed visible projection.
- An instance is in Archive if its own `archived` flag is true **or** its parent recurrence rule is archived. This handles previous iOS `archiveRoutine` which archives the rule without flipping each child flag.
- Search uses locale-aware case-insensitive matching; results ordered by original due time then UUID. Outcome labels come from stored `UNKNOWN/DONE/SKIPPED`.
- New `QuietPrecisionHistoryView` supports History/Archive, title search, outcome picker, 25-row visible increments and detail sheets showing existing audit events.
- Reachable from Quiet Precision Library and reminder management; iOS 16-compatible UI (no iOS 17-only empty-state component).

## Data and UX invariants

- UNKNOWN/unanswered is never reclassified as Done or Skipped.
- History/Archive UI performs no writes or schedule changes.
- Archive browsing is not restore/unarchive: archived history is read-only and there is **no import operation**.
- Original due timestamps preserve historical slots separately from snoozed current due timestamps.
- Full local history and audit events remain available via the store; UI may show a bounded page at a time.
- No silent correction, deletion or replay of archived schedules.
- Real iPhone/Android accessibility, large text, huge archive performance and signed updates remain release quality gates.

## Tests

Android `CompleteArchiveHistoryInstrumentedTest` exercises archived one-off and recurring slots, explicit Done and Unknown filtering, missing future-only slots, literal search wildcard handling, original due integrity and stable pagination. Test-only package database is isolated from users.

iOS `CompleteArchiveHistoryTests` exercises archived single reminders and archived rule children, case-insensitive search, outcome filters, excludes future UNKNOWN, and proves that reads preserve persisted snapshot bytes. Both platforms retain earlier reminder/persistence tests.

## Release and next steps

- Android versionCode 15 / versionName `0.6.4-r3e-alpha`, unchanged application ID. The CI artifact is `android-reminder-quiet-precision-r3e-debug` if the build passes.
- Do not call R3e build verified until **all** Kotlin/Android and iOS Simulator CI results pass.
- Still pending: production branding/logo/welcome animation, tracker/routine/goal domain models, weekly evidence-backed insights, V0.5e safe cross-platform portable import+rollback before data migration, signed packages, real-device alarms, accessibility and performance QA.
