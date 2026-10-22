# Durable event store and scheduling outbox — v1 spike

**Date:** 2026-10-22  
**Status:** Portable SQLite reference implementation and CI integration-test contract. Android/iOS native database adapters not implemented yet.

## Purpose

A reminder cannot be declared "reliable" simply because the OS accepted a scheduled notification. We must persist the user's intent, response, and pending OS work so that each can be examined and reconciled after app restarts and partial failures.

This spike introduces a portable relational format at `storage/schema/v1.sql` and a Python `sqlite3` reference implementation at `tools/sqlite_reference.py`. It is an *executable specification*, **not** a production Kotlin database adapter, iOS integration, or device-delivery test.

## Schema

- `reminder_definitions`: persistent reminder identity, title, active/paused/archived state.
- `occurrences`: immutable scheduled slot identity and due time; separately tracked current **UNKNOWN / DONE / SKIPPED** user outcome; snooze/acknowledgement metadata.
- `action_events`: stable idempotency key for explicit Done, Skip, Snooze, Acknowledge and Reopen user actions. Duplicate callbacks cannot create duplicate action rows.
- `schedule_outbox`: durable REGISTER/CANCEL commands with PENDING/FAILED/APPLIED/SUPERSEDED status and attempt history. FAILED can retry; SUPERSEDED never means successfully applied.
- `platform_registrations`: current known native OS handles; these are **per-device projections**, not cross-device backup data.

Indices support due-time history and pending-work retrieval. Foreign keys and CHECK constraints guard referential integrity and allowed states. SQL schema declares `PRAGMA user_version=1`; every connection must enable foreign keys.

## Atomic operations

1. Creating a reminder commits its definition, occurrence, and REGISTER job in **one transaction**.
2. Explicit Done/Skip records a response, updates current user outcome, supersedes unused REGISTER jobs, and enqueues CANCEL in **one transaction**.
3. Explicit Snooze records the action, keeps outcome UNKNOWN, supersedes unused REGISTER jobs, and enqueues CANCEL then REGISTER for the new time in **one transaction**.
4. Acknowledge does not imply Done. No-response is not an event that pretends the user Skipped.
5. Marking a native OS operation successfully applied also updates the local registration projection and job state in a database transaction. The OS call itself remains a separate side effect.
6. Every action ID is globally unique; repeating an action with that ID must be idempotent. A rejected state change or outbox-ID collision must roll back the *entire* transaction.

## Crash window / reliability boundary

There is **no distributed transaction** spanning SQLite and Android AlarmManager or Apple UNUserNotificationCenter. A process can die between requesting an OS operation and recording it locally. Real adapters MUST:

- Use deterministic/consistent native notification request IDs tied to occurrence IDs and revisions.
- Be idempotent on REGISTER and CANCEL so the outbox executor can retry.
- Observe supported platform state, reconcile with stored reminder intentions on wake/launch/eligible system events, and avoid duplicate native alert presentation where possible.
- Suppress obsolete register commands and prioritize cancellation when an occurrence was resolved or snoozed.
- Handle missing permissions, denied scheduling and uncertain OS callbacks without silently changing user outcomes.
- Retain outbox failure reasons without storing sensitive reminder title/note content in diagnostics.

In this spike, the reference caller controls OS-job acknowledgement explicitly; no real platform API is invoked. `pending_operations()` returning due jobs is not an execution claim.

## Verification

Run `python3 -m unittest discover -s tests -p test_sqlite_reference.py -v`.

Tests cover foreign key constraints, atomic create, duplicate callbacks, conflict rollback, explicit action states, valid/invalid snooze replacement, obsolete job suppression, failed/retried OS operations, registration projections, time-window metric denominators, future-schema refusal, and durable database reopen after process-like shutdown.

GitHub Actions now requests this suite independently of native platform builds. Actual pass/fail is recorded in the linked CI run, not inferred from source code.

## Known limitations

- This schema is a **candidate contract**. A tested upgrade strategy from v1 to v2 and encrypted portable backup/restore remain future work. It has no automatic destructive migrations.
- This implementation is synchronous Python for standard-library SQLite integration tests only, not the final mobile data layer.
- Recurrence scheduling, timezone resolution, exact-alarm permission, real OS callback delivery and mobile UI are outside its scope.
- Schedules can change while OS effects are in flight. A production adapter will need an operation leasing/serialization protocol, stable native request identity, cancellation ordering, late-callback checks and restart reconciliation.
- Sensitive medical, financial or personal tracking notes are not in sample data; public CI/test fixtures contain only synthetic records.
- Do not claim full Android/iOS persistence until native adapters and physical device recovery tests exist.

## Follow-up sequence

1. Verify CI SQLite contract tests; fix failures before promoting candidate schema.
2. Implement durable database adapters on both targets, selecting KMP Room/SQLDelight or another validated cross-platform SQLite layer. Evaluate migration and encryption strategy.
3. Connect the scheduler outbox to native alert registration with stable native identifiers and state reconciliation.
4. Add concurrent transaction, process-kill and on-device restart tests.
5. Integrate tracker measurements and goals into the common data layer without weakening reminder transaction invariants.

Primary references:
- Android SQLite transactions: https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase
- Android Room for KMP: https://developer.android.com/kotlin/multiplatform/room
- Apple SQLite: https://developer.apple.com/documentation/sqlite
