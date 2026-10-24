# R3d — Safe Replacement of Existing Recurring Schedules

**Date:** 2026-10-24  
**Branch:** `feature/quiet-precision-r3d-safe-schedule-replace`  
**State:** Native source and regression tests added; GitHub CI must pass before calling the alpha build verified.

## Core user contract

Editing existing recurrence definitions in place would violate historic slot-key constraints and could silently reclassify or duplicate already-generated occurrences. R3d instead adds an explicit **Replace future schedule** operation.

- User selects new title, a future anchor, daily/weekly/monthly repeat and optional weekdays.
- A new, independent schedule ID takes responsibility for future occurrences.
- The old schedule becomes **Archived**, and all its old reminder occurrences remain preserved as archived records with the same Done/Skipped/Unknown outcomes and original audit history.
- The user can browse **Archived schedules → Past outcomes**.
- Old pending system notifications are canceled/reconciled, and new future notifications are registered through existing scheduler outbox/native APIs.
- Paused schedules remain paused on replacement; no unexpected alarms are started.
- This is not a silent in-place edit, a history deletion or cross-device backup import.

## Android atomicity

`ReminderDatabase.replaceRoutine(expected,title,anchorMs,mode,daysMask)` executes in a single SQLite transaction:

1. Reject malformed title, past anchor, invalid repeat mode, invalid weekly mask.
2. Read the persisted rule and compare every field to the expected model, since V3 recurrence rules have no revision column. Reject stale edits without writing.
3. Mark original rule archived, mark its old occurrences archived and increment their revisions to invalidate stale callbacks; preserve exact outcomes and their events and append `RULE_REPLACED` audit entries.
4. Queue scheduler cancellations through the existing outbox and insert the replacement rule with a fresh ID (preserving pause state).
5. Generate new future occurrences only if active. Transaction rollback covers all DB changes if a failure occurs.

The UI calls `ReminderAlarmEngine.dismiss` on old pending IDs and flushes the existing scheduler outbox only **after** successful commit. The outbox is designed to retry native job failures.

## iOS snapshot atomicity

`IOSReminderStore.replaceRecurring(expected,title,dueAt,pattern,weekdayMask)` validates the complete expected rule against the current in-memory state, then constructs a replacement snapshot in memory:

1. Archive old rule and instances, increment old instance revisions while preserving their outcomes and historical events.
2. Append a new active or paused rule ID and up to 7 eligible future slots, with new IDs, creating explicit event records.
3. Write all rules, reminders and events as one atomically replaced local JSON snapshot using the existing protected-file save.
4. After persisted success only, remove pending/delivered OS notifications for original IDs and invoke existing `reconcile()`.

iOS re-scheduling depends on user notification permission, Focus, platform limits and actual device state. Saving the event record does not prove the user received the notification.

## UI integration

- Android Quiet Precision management screen exposes Replace future schedule and Archived schedules / recorded history.
- Android native reminder editor accepts a `rule_id`, preloads title/repeat/weekdays and the next future local time, and **explicitly states the archival behavior**.
- iOS management list exposes Replace future schedule sheet, with previous inputs and no Once option, and Archived schedules history.
- Both keep original recovery/backup tools available.

## Regression cases

Android `SafeScheduleReplaceInstrumentedTest` tests:
- One old Done outcome preserved and audited, fresh replacement ID, generated future slot.
- Invalid inputs and a stale rule snapshot produce **no replacement**.
- Paused replacement remains paused and can later be resumed.

iOS `SafeScheduleReplaceTests` tests:
- Old Done outcome and audit events preserved in persisted snapshot, fresh replacement schedule survives reloading.
- Invalid/stale request leaves snapshot bytes untouched.

## Explicit limits and release gates

- **Does not support in-place mutable recurring rule ID or automatic lineage grouping**. Old and new are separate, with original visible in Archived schedules.
- Time-zone and DST edge cases must be tested on physical devices; immutable original due times are preserved for audits.
- OS notifications and permission behavior require real-phone reliability testing; unit/XCTest/Android emulator success is not a production reliability certificate.
- RPOSB2 portable import/rollback is still **not implemented**; no user data migration or storage identity change is attempted.
- No account, background service, cloud sync, AI inference or reclassification of Unknown is introduced.
- Android alpha versionCode **14**, versionName **0.6.3-r3d-alpha**, same application ID as R3c. Stable signing and safe update compatibility remain unverified.
- Before production release, validate full restore, archive browser scale, secure signing, accessibility, localization and native notification reliability.

## Next steps after R3d build verification

1. Real-device / accessibility QA for schedule replacement and notification queue repair.
2. Native archive search and robust correction/history tools, without losing auditability.
3. V0.5e safe portable RPOSB2 import and rollback **before** any persistence migration.
4. Implement real tracker data, routines/goals and evidence-backed 7/28/90-day analysis.
