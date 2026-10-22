# Data and Analytics Contract — V1 (Draft)

**State:** proposed for implementation; not executed or validated.  
**Purpose:** create trustworthy local activity history and reproducible insights.

## 1. Storage architecture

A local transactional relational store is the source of truth for reminder definitions, occurrences, trackers, actions and observations. Kotlin Multiplatform persistence implementation (Room KMP versus SQLDelight or other) remains open until evaluated.

Keep *current operational state* in indexed tables and meaningful changes in immutable event records. Do not try to rebuild all current state from a huge event stream in V1; avoid unnecessary event-sourcing complexity.

All records need stable opaque IDs, schema version, created_at, updated_at where applicable, and source/device metadata where useful. Sensitive content must remain local unless user explicitly exports/syncs.

## 2. Candidate relational entities

| Entity | Required fields | Essential constraints |
|---|---|---|
| ReminderDefinition | id, title, priority, status, created_at, updated_at | title nonempty; soft-delete policy explicit |
| ScheduleRule | id, reminder_id, recurrence kind, local time, zone policy, IANA zone, start/end, rule_revision | referential integrity; normalized recurrence |
| Occurrence | id, reminder_id, rule_revision, logical_date/slot, due_at_utc, due_local, outcome | unique logical identity per revision and slot |
| ScheduleRegistration | id, occurrence_id, platform, platform_handle, requested_at, accepted_state, error | one active registration per role |
| AlertAttempt | id, occurrence_id, attempted_at, callback_at?, adapter_result, failure_code? | evidence of attempt only, not confirmed viewing |
| UserAction | id, occurrence_id?, action, recorded_at, effective_at?, correlation_id | unique correlation_id for deduplication |
| TrackerDefinition | id, name, kind, unit?, constraints?, active | check-in/count/numeric types initially |
| TrackerEntry | id, tracker_id, value, value_kind, occurred_at, recorded_at, note?, source | validation by type and unit |
| Routine | id, title, schedule association?, status | no duplicated counters |
| Goal | id, tracker_id or routine_id, target, period, baseline? | measurable target definition |
| InsightRecord | id, rule_version, period, finding, sample_size, evidence_refs, limitation | no unsupported causal statements |
| EventHistory | event_id, entity_type, entity_id, event_type, occurred_at, recorded_at, payload_version, metadata | append-only for audit-relevant actions |
| Diagnostic | id, platform, permission/capability type, observed_at, status | separate capability from actual delivery |

**Avoid duplicating secrets or raw tracker notes in analytics tables.** Model attachments separately if P1 photos are added.

## 3. Event specification

Schema envelope:
```json
{
  "event_id": "uuid",
  "entity_id": "opaque-id",
  "entity_type": "occurrence",
  "event_type": "USER_MARKED_DONE",
  "occurred_at": "2026-10-22T08:05:00+05:30",
  "recorded_at": "2026-10-22T08:05:08+05:30",
  "correlation_id": "action-uuid",
  "schema_version": 1,
  "payload": {"action_source": "notification"}
}
```
Illustrative only. Timestamps are normalized to instants for comparisons, retaining originating local time/zone for display and recurrence. Events such as reminder_created, occurrence_scheduled, alert_callback_observed, user_acknowledged, snoozed, marked_done, marked_skipped, corrected, tracker_entry_added and restored are distinct. Never emit `NOTIFICATION_SEEN` without verifiable evidence; iOS/Android notification visibility cannot generally be inferred.

## 4. Analytics definitions (must be exact)

Define an analysis **period** in the user's selected zone; include only occurrence slots with original due time in that period. Exclude deleted/canceled future slots that were never due; retain due historical slots unless a user explicitly elects to delete their history.

For N eligible due occurrences in a period:
- `done_count` = number with current explicit DONE outcome;
- `skipped_count` = number with explicit SKIPPED outcome;
- `unresolved_count` = N - done_count - skipped_count (including snoozed/no response still unresolved);
- `confirmed_completion_coverage` = done_count / N (label clearly; not presented as entire task success);
- `known_outcome_completion_rate` = done_count / (done_count + skipped_count), only when denominator > 0;
- `unknown_share` = unresolved_count / N;
- `on_time_response` = explicit applicable user response within configured lateness window; no inferred success;
- `response_lag` = recorded/effective user action time minus original due time (show negative values for early logged actions if permitted).

Display both **confirmed completion** and **unknown share** prominently so users cannot mistake missing data for failure. When N = 0, show 'Not enough data', not 0% or 100%. Acknowledge is not Done.

For trackers:
- numeric trend = values with explicit unit and timestamp; no conversion without known compatible units;
- count totals = sum of recorded counts; distinguish entry count from quantity;
- check-in adherence = confirmed yes divided by due opportunities only if schedule exists; otherwise report frequency, not adherence;
- goal progress = tracker/routine evidence against explicit target; show unknown coverage.

**Historical corrections** must recompute aggregates deterministically. Rate denominator, timezone, version and inclusion/exclusion policy should be available to the insight detail view.

## 5. Insight engine rules

- Rules consume a versioned analytic snapshot, not unsourced AI guesses.
- Every insight stores its rule ID/version, window, evidence record IDs, metric values, sample size and limitations.
- Avoid pattern claims on tiny windows. **Proposed default:** at least 10 eligible events across 2 weeks for behavioural pattern suggestions, and compare groups only if both have at least 5 eligible events; thresholds subject to validation.
- Explain missing-data risk: if unresolved share > 30%, avoid strong comparative adherence claims.
- Never say 'because' for observational co-occurrence; label correlations as associations.
- Never infer illness, diagnose conditions or direct medication dosing from tracker changes.
- Give user controls to hide insight, flag as unhelpful and request calculation explanation.
- Provide a traceable route back to the underlying entries with privacy-aware presentation.

Example: 'Across the last 28 days, 5 of 8 afternoon check-ins were confirmed, compared with 11 of 12 morning check-ins; 4 scheduled entries remain unresolved. Your afternoon confirmations were less frequent in this period.' Do not infer why.

## 6. Data access and performance

Indexed by occurrence due_at_utc, reminder_id, tracker_id and time, with bounded pagination. Use database query aggregation plus small cached summaries invalidated by relevant event changes. No upload required for core analytics. Schedule CPU-heavy recomputations opportunistically through platform-safe background tasks, and allow foreground fallback.

Plan schema migrations for each version, foreign-key integrity, write-ahead/logging settings as appropriate and safe rollback on failed restore. Store OS scheduling handles as disposable projections; never transfer them verbatim between devices.

## 7. User rights and safety

- Clear user data from settings, with confirmation and consequences.
- Export a versioned encrypted backup using a user-controlled passphrase and integrity authentication; no silent cloud upload.
- Recovery design must account for wrong password, corrupted file, outdated schema, partial import and duplicate occurrence identities.
- Backup restore should be atomic where practical and offer a preview of date/source before overwrite.
- Track locally with no forced account creation; optional future sync is a distinct explicitly approved specification.
- Public development logs and crash reports must never contain activity titles, notes, secrets or health details.

## 8. Verification examples

1. 10 due occurrences: 6 Done, 1 Skipped, 3 Unresolved → confirmed coverage 60%, known-outcome completion 6/7 ≈ 85.7%, unknown share 30%.
2. All 10 dismissed but none explicitly Done/Skipped → coverage 0%, unknown 100%; do **not** report 100% failed.
3. Two identical notification action callbacks → one UserAction and one final Done state.
4. Correct historical tracker value → recomputed trend reflects correction; audit event retains evidence.
5. Traveling across zones → period assignment uses user's chosen report zone, independent of historical due UTC semantics.
6. Empty sample → insufficient data; no trend fabricated.
7. Mixed units → never aggregate as one numeric series without validated conversion.

## 9. Open decisions

- Whether to retain an immutable correction event for every manual edit or only audit-significant changes.
- Backup key derivation/KDF, authenticated encryption and passphrase recovery UX.
- Versioned local insight rules, evidence storage volume and pruning options.
- Analytics time-window customization and comparative statistical thresholds.
- Exact KMP persistence library, encryption-at-rest model and platform secure key stores.

No database schema, migration or analytics code has been implemented at this stage.
